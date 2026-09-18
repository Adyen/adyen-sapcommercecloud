package com.adyen.commerce.connector.payment;

import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_CC;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_SCHEME;

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
 * Tells the Adyen /payments request, while it is still being assembled, that this cart funds a
 * subscription and therefore has to leave a reusable token behind — and refuses the checkout before the
 * shopper can be charged when the method they picked cannot produce one.
 *
 * <p>The contract fields themselves belong to {@link RecurringContractHelper#applySubscriptionContract},
 * which owns {@code storePaymentMethod}, the processing model and the deprecated flags for every payment
 * path; writing them out here would overrule, from the last step of the pipeline, what the payment method
 * handlers just decided through that same helper.</p>
 *
 * <p>What counts as a subscription product is {@link SubscriptionProductRule}'s decision, the same bean
 * {@code DefaultSubscriptionOrderActivator} asks after the money has moved. A rule that cannot answer is
 * fatal here and is not there: this runs before the shopper is charged, where degrading to "not a
 * subscription" sends the request out untokenized and leaves the activator with a paid order it can never
 * turn into a subscription. A failed checkout is recoverable; that is not.</p>
 *
 * <p>A store whose {@code activeBillingPlatform} names a platform with no connector bean registered is
 * refused the same way, which fails <em>every</em> checkout in that store, ordinary carts included. "No
 * connector" does not mean "nothing here is a subscription" — the plan mappings and subscription products
 * outlive the connector being removed from the deployment — it means the question cannot be answered.
 * Letting the cart through would send the payment out untokenized and the activator would dead-letter the
 * attempt at once, since {@code ConnectorNotConfiguredException} is terminal for the retry policy. Narrowing
 * this to genuine subscription carts needs a way to classify a product without the connector.</p>
 *
 * <p>External token import is limited to cards until method-specific contracts for wallets and alternative
 * payment methods exist, and that limit can only be enforced approximately here: a saved method arrives as
 * {@code adyen_oneclick_<storedPaymentMethodId>} and the id carries no type. Saved methods are offered to
 * the shopper filtered by supported shopper interaction only (see
 * {@code DefaultAdyenCheckoutFacade#getStoredOneClickPaymentMethods}), so a stored PayPal or SEPA mandate
 * can appear among them, and the cart keeps only the ids ({@code Cart.adyenStoredCards} is a
 * {@code StringSet}), not the {@code type} that {@code /paymentMethods} returned. What is enforced is that
 * the handler which ran produced a card token reference, which cannot rule out a non-card behind a
 * saved-method selection because {@code OneClickPaymentHandler} builds {@code CardDetails} for all of
 * them.</p>
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

		// findConnector rather than getActiveConnector: answering the missing-connector case here keeps it out
		// of a catch wide enough to also cover the cart inspection, which would swallow the refusal below.
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

		if (!containsSubscriptionProduct(cart, connector))
		{
			return;
		}

		final String paymentMethod = cartData == null ? null : StringUtils.trimToNull(cartData.getAdyenPaymentMethod());
		if (!isTokenizableCard(paymentMethod, paymentRequest))
		{
			// Typed rather than an IllegalArgumentException: this is the shopper having picked a method that
			// cannot fund renewals, not a bug, and everything above flattens an unrecognised failure here into
			// a generic authorization error the storefront cannot turn into "pick a card".
			throw new RecurringContractHelper.TokenizationNotSupportedException(
					"Payment method '" + StringUtils.defaultString(paymentMethod, "<missing>")
							+ "' cannot leave a reusable token behind, which " + connector.platform()
							+ " subscriptions require; the shopper has to pay with a card");
		}

		RecurringContractHelper.applySubscriptionContract(paymentRequest);
	}

	/**
	 * Classifies <em>every</em> entry, even once a subscription product has been found, so that an entry the
	 * rule cannot classify is refused whichever position it sits in. Stopping at the first match would make a
	 * cart holding one mapped product and one undecidable product succeed or fail depending on entry order,
	 * and disagree with the activator, which looks at all of them.
	 *
	 * @throws IllegalStateException if the rule could not classify an entry, which is the fail-closed half
	 *         described in the class javadoc
	 */
	protected boolean containsSubscriptionProduct(final CartModel cart, final SubscriptionBillingConnector connector)
	{
		if (cart.getEntries() == null)
		{
			return false;
		}
		boolean found = false;
		for (final AbstractOrderEntryModel entry : cart.getEntries())
		{
			final ProductModel product = entry == null ? null : entry.getProduct();
			if (product == null || StringUtils.isBlank(product.getCode()))
			{
				continue;
			}
			try
			{
				found |= subscriptionProductRule.isSubscriptionProduct(connector, product);
			}
			catch (final SubscriptionProductUndecidableException e)
			{
				// Unchecked so it can leave decoratePaymentRequest, whose signature belongs to
				// AdyenPaymentRequestDecorator and carries no checked exception.
				throw new IllegalStateException("Cannot decide whether product '" + product.getCode()
						+ "' is a subscription product; refusing to authorize a payment that may need a reusable token",
						e);
			}
		}
		return found;
	}

	/**
	 * Whether this payment can be vaulted as a card token the billing platform will be able to charge again.
	 * See the class javadoc for what the saved-method half of this can and cannot establish.
	 */
	protected boolean isTokenizableCard(final String paymentMethod, final PaymentRequest paymentRequest)
	{
		if (PAYMENT_METHOD_SCHEME.equals(paymentMethod) || PAYMENT_METHOD_CC.equals(paymentMethod))
		{
			return true;
		}
		// The prefix marks any saved method, not a saved card, so the handler must also have turned it into a
		// card token reference. Asked of RecurringContractHelper because the same predicate decides
		// storePaymentMethod one step later.
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
