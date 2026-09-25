package com.adyen.commerce.connector.payment;

import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_CC;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_SCHEME;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.SubscriptionProductUndecidableException;
import com.adyen.commerce.connector.product.SubscriptionProductRule;
import com.adyen.commerce.connector.registry.SubscriptionBillingConnectorRegistry;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;
import com.adyen.commerce.decorator.AdyenPaymentRequestDecorator;
import com.adyen.commerce.services.impl.RecurringContractHelper;
import com.adyen.model.checkout.PaymentRequest;
import com.adyen.v6.model.RequestInfo;
import com.adyen.v6.util.AdyenUtil;

import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.order.CartService;
import de.hybris.platform.store.BaseStoreModel;

/**
 * Makes a payment that funds a subscription leave a reusable card token behind, and refuses the checkout
 * before the shopper is charged when it cannot be turned into exactly one subscription.
 *
 * <p>The contract fields are set by {@link RecurringContractHelper#applySubscriptionContract}. Classification
 * is {@link SubscriptionProductRule}'s, shared with the activator. Unlike the activator this fails closed:
 * a product that cannot be classified, or a store whose platform has no connector, refuses the payment,
 * because an untokenized payment for a subscription cannot be repaired afterwards.</p>
 *
 * <p>A saved method ({@code adyen_oneclick_<id>}) carries no type, so "card" is checked only as far as the
 * handler having produced a card token reference.</p>
 */
public class SubscriptionPaymentRequestDecorator implements AdyenPaymentRequestDecorator
{
	private static final Logger LOG = LoggerFactory.getLogger(SubscriptionPaymentRequestDecorator.class);

	private CartService cartService;
	private SubscriptionBillingConnectorRegistry connectorRegistry;
	private SubscriptionProductRule subscriptionProductRule;

	@Override
	public void decoratePaymentRequest(final PaymentRequest paymentRequest, final CartData cartData,
			final PaymentRequest originPaymentsRequest, final RequestInfo requestInfo, final CustomerModel customerModel)
	{
		final CartModel cart = cartService.getSessionCart();
		final BaseStoreModel store = cart == null ? null : cart.getStore();
		final BillingPlatform platform = store == null ? null : store.getActiveBillingPlatform();
		if (platform == null)
		{
			return;
		}

		final SubscriptionBillingConnector connector = connectorRegistry.findConnector(platform).orElse(null);
		if (connector == null)
		{
			LOG.error("Base store '{}' has {} as its active billing platform but no connector is registered for it. "
					+ "Refusing the payment: without the connector no product can be classified, so letting this "
					+ "through risks charging for a subscription nothing can activate.", store.getUid(), platform);
			throw new IllegalStateException("Base store '" + store.getUid() + "' declares " + platform
					+ " as its active billing platform but no connector is registered for it; refusing to authorize "
					+ "a payment whose subscription content cannot be determined");
		}

		final Map<String, Long> units = subscriptionUnits(cart, store, connector);
		if (units.isEmpty())
		{
			return;
		}
		final long totalUnits = units.values().stream().mapToLong(Long::longValue).sum();
		if (units.size() > 1 || totalUnits > 1)
		{
			throw new RecurringContractHelper.SubscriptionCartNotSupportedException("Cart holds subscription units "
					+ units + " but one order can activate one subscription with quantity one");
		}

		final String paymentMethod = cartData == null ? null : StringUtils.trimToNull(cartData.getAdyenPaymentMethod());
		if (!isTokenizableCard(paymentMethod, paymentRequest))
		{
			throw new RecurringContractHelper.TokenizationNotSupportedException(
					"Payment method '" + StringUtils.defaultString(paymentMethod, "<missing>")
							+ "' cannot leave a reusable token behind, which " + connector.platform()
							+ " subscriptions require; the shopper has to pay with a card");
		}

		RecurringContractHelper.applySubscriptionContract(paymentRequest);
	}

	/**
	 * Subscription units in the cart, per product code. Every entry is classified, so an undecidable one refuses
	 * the payment wherever it sits.
	 *
	 * @throws IllegalStateException if the rule could not classify an entry
	 */
	protected Map<String, Long> subscriptionUnits(final CartModel cart, final BaseStoreModel store,
			final SubscriptionBillingConnector connector)
	{
		final Map<String, Long> units = new LinkedHashMap<>();
		if (cart.getEntries() == null)
		{
			return units;
		}
		final Map<String, Boolean> verdicts = new HashMap<>();
		for (final AbstractOrderEntryModel entry : cart.getEntries())
		{
			final ProductModel product = entry == null ? null : entry.getProduct();
			if (product == null || StringUtils.isBlank(product.getCode()))
			{
				continue;
			}
			if (isSubscriptionProduct(verdicts, connector, store, product))
			{
				units.merge(product.getCode(), unitsOf(entry), Long::sum);
			}
		}
		return units;
	}

	/** The rule's verdict, asked once per product code. */
	protected boolean isSubscriptionProduct(final Map<String, Boolean> verdicts,
			final SubscriptionBillingConnector connector, final BaseStoreModel store, final ProductModel product)
	{
		final Boolean known = verdicts.get(product.getCode());
		if (known != null)
		{
			return known;
		}
		try
		{
			final boolean verdict = subscriptionProductRule.isSubscriptionProduct(connector, store, product);
			verdicts.put(product.getCode(), verdict);
			return verdict;
		}
		catch (final SubscriptionProductUndecidableException e)
		{
			// Unchecked: AdyenPaymentRequestDecorator declares no checked exception.
			throw new IllegalStateException("Cannot decide whether product '" + product.getCode()
					+ "' is a subscription product; refusing to authorize a payment that may need a reusable token", e);
		}
	}

	/** An entry counts as at least one unit, so a missing quantity cannot hide a subscription. */
	protected long unitsOf(final AbstractOrderEntryModel entry)
	{
		final Long quantity = entry.getQuantity();
		return quantity == null ? 1L : Math.max(1L, quantity);
	}

	/** Whether this payment can be vaulted as a card token the platform can charge again. */
	protected boolean isTokenizableCard(final String paymentMethod, final PaymentRequest paymentRequest)
	{
		if (PAYMENT_METHOD_SCHEME.equals(paymentMethod) || PAYMENT_METHOD_CC.equals(paymentMethod))
		{
			return true;
		}
		// A saved method must also have been turned into a card token reference by its handler.
		return StringUtils.isNotBlank(paymentMethod) && AdyenUtil.isOneClick(paymentMethod)
				&& RecurringContractHelper.isStoredPaymentMethodReused(paymentRequest);
	}

	public void setCartService(final CartService cartService)
	{
		this.cartService = cartService;
	}

	public void setConnectorRegistry(final SubscriptionBillingConnectorRegistry connectorRegistry)
	{
		this.connectorRegistry = connectorRegistry;
	}

	public void setSubscriptionProductRule(final SubscriptionProductRule subscriptionProductRule)
	{
		this.subscriptionProductRule = subscriptionProductRule;
	}
}
