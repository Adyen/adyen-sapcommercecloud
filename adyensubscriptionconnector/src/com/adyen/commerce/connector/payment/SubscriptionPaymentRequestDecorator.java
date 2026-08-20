package com.adyen.commerce.connector.payment;

import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_CC;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_SCHEME;

import org.apache.commons.lang3.StringUtils;

import com.adyen.commerce.connector.dto.PlanResolutionRequest;
import com.adyen.commerce.connector.exception.BillingException;
import com.adyen.commerce.connector.exception.PlanNotMappedException;
import com.adyen.commerce.connector.registry.SubscriptionBillingConnectorRegistry;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;
import com.adyen.commerce.decorator.AdyenPaymentRequestDecorator;
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
 * Fails a subscription checkout before Adyen authorization when the selected payment method cannot
 * satisfy the connector's current token contract. Recurly external-token import is deliberately
 * limited to cards until method-specific contracts for wallets and alternative methods are added.
 */
public class SubscriptionPaymentRequestDecorator implements AdyenPaymentRequestDecorator
{
	private CartService cartService;
	private SubscriptionBillingConnectorRegistry connectorRegistry;

	@Override
	public void decoratePaymentRequest(final PaymentRequest paymentRequest, final CartData cartData,
			final PaymentRequest originPaymentsRequest, final RequestInfo requestInfo, final CustomerModel customerModel)
	{
		final CartModel cart = cartService.getSessionCart();
		final BaseStoreModel store = cart == null ? null : cart.getStore();
		if (store == null || store.getActiveBillingPlatform() == null)
		{
			return;
		}

		final SubscriptionBillingConnector connector = activeConnector(store);
		if (!containsSubscriptionProduct(cart, connector))
		{
			return;
		}

		final String paymentMethod = cartData == null ? null : StringUtils.trimToNull(cartData.getAdyenPaymentMethod());
		if (!isSupportedCardMethod(paymentMethod))
		{
			throw new IllegalArgumentException("Payment method '" + StringUtils.defaultString(paymentMethod, "<missing>")
					+ "' is not supported for " + connector.platform()
					+ " subscriptions; select a credit or debit card");
		}

		// Do not depend on the legacy adyenv6subscription decorator: generic connector products may be
		// identified by plan mapping alone and still require Adyen to create a reusable token. The
		// Checkout API rejects storePaymentMethod when either legacy flag is also present (140_405),
		// so normalise requests built by older storefront handlers before enabling tokenisation.
		paymentRequest.setEnableRecurring(null);
		paymentRequest.setEnableOneClick(null);
		paymentRequest.setStorePaymentMethod(true);
		paymentRequest.setRecurringProcessingModel(PaymentRequest.RecurringProcessingModelEnum.SUBSCRIPTION);
	}

	protected SubscriptionBillingConnector activeConnector(final BaseStoreModel store)
	{
		try
		{
			return connectorRegistry.getActiveConnector(store);
		}
		catch (final BillingException e)
		{
			throw new IllegalStateException("Subscription connector is not available for store '" + store.getUid() + "'", e);
		}
	}

	protected boolean containsSubscriptionProduct(final CartModel cart, final SubscriptionBillingConnector connector)
	{
		if (cart.getEntries() == null)
		{
			return false;
		}
		for (final AbstractOrderEntryModel entry : cart.getEntries())
		{
			final ProductModel product = entry == null ? null : entry.getProduct();
			if (product == null || StringUtils.isBlank(product.getCode()))
			{
				continue;
			}
			try
			{
				connector.resolvePlan(new PlanResolutionRequest(product.getCode(), java.util.Map.of()));
				return true;
			}
			catch (final PlanNotMappedException e)
			{
				// Ordinary product; inspect the remaining entries.
			}
			catch (final BillingException | RuntimeException e)
			{
				throw new IllegalStateException("Cannot validate subscription product '" + product.getCode()
						+ "' before payment", e);
			}
		}
		return false;
	}

	protected boolean isSupportedCardMethod(final String paymentMethod)
	{
		return PAYMENT_METHOD_SCHEME.equals(paymentMethod)
				|| PAYMENT_METHOD_CC.equals(paymentMethod)
				|| (StringUtils.isNotBlank(paymentMethod) && AdyenUtil.isOneClick(paymentMethod));
	}

	public void setCartService(final CartService cartService)
	{
		this.cartService = cartService;
	}

	public void setConnectorRegistry(final SubscriptionBillingConnectorRegistry connectorRegistry)
	{
		this.connectorRegistry = connectorRegistry;
	}
}
