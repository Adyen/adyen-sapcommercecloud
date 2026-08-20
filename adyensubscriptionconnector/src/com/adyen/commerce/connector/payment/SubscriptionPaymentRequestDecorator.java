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
 * <h3>Who decides the contract</h3>
 * <p>Not this class. It hands the subscription signal to
 * {@link RecurringContractHelper#applySubscriptionContract}, which owns {@code storePaymentMethod}, the
 * processing model and the deprecated flags for every payment path. Writing those fields out by hand here
 * would mean overruling, from the last step of the pipeline, a decision the payment method handlers had
 * just taken through that same helper — the two would agree only for as long as nobody touched either.</p>
 *
 * <h3>Who decides what counts as a subscription product</h3>
 * <p>Also not this class: {@link SubscriptionProductRule}, the same bean
 * {@code DefaultSubscriptionOrderActivator} asks after the money has moved. What this class does own is
 * what to do when the rule cannot answer, and there it deliberately parts company with the activator. A
 * resolver that throws is fatal here and is not there. The activator runs after authorization, where
 * refusing to answer would strand a paid order, so it journals the failure and lets the order stand. This
 * runs before the shopper is charged, where degrading to "not a subscription" is the expensive answer: the
 * request would go out without forced tokenization, the order would be placed and paid, and the activator
 * would then be left with an untokenized payment it cannot turn into a subscription. Failing the checkout
 * is recoverable; charging for a subscription that can never be activated is not.</p>
 *
 * <h3>A missing connector fails the same way, and it is blunt</h3>
 * <p>A store whose {@code activeBillingPlatform} names a platform with no connector bean registered gets the
 * same refusal, and the cost is worth stating plainly: <em>every</em> checkout in that store fails, including
 * carts holding nothing but ordinary products. That is deliberate but not comfortable. It is tempting to read
 * "no connector" as "then nothing here is a subscription", and that reading is wrong — the plan mapping rows
 * and the subscription products survive the connector being removed from the deployment, so what this
 * condition establishes is only that the question <em>cannot be answered</em>, not that the answer is no.
 * Letting the cart through on that basis would send the payment out untokenized, and the activator would then
 * meet the same missing connector and dead-letter the attempt immediately, because
 * {@code ConnectorNotConfiguredException} is terminal for the retry policy: a paid order, no subscription, and
 * no retry. A store-wide outage is loud and recoverable by fixing the configuration; that is not.</p>
 *
 * <p>Narrowing this to only the carts that really are subscription carts needs a way to classify a product
 * without the connector — asking the remaining registered connectors, or reading the plan mapping tables
 * directly. Until that exists, the blunt version is the safe one.</p>
 *
 * <h3>Why only cards, and how exactly that is enforced</h3>
 * <p>External token import is deliberately limited to cards until method-specific contracts for wallets and
 * alternative payment methods exist. That limit can only be enforced <em>approximately</em> at this point,
 * and the gap is worth stating rather than hiding: a saved method reaches us as
 * {@code adyen_oneclick_<storedPaymentMethodId>}, and the id carries no type. The saved methods offered to
 * the shopper are filtered by supported shopper interaction only — see
 * {@code DefaultAdyenCheckoutFacade#getStoredOneClickPaymentMethods} — so a stored PayPal or SEPA mandate
 * can legitimately appear among them, and the cart keeps only the ids
 * ({@code Cart.adyenStoredCards} is a {@code StringSet}), not the {@code type} that
 * {@code /paymentMethods} returned alongside them.</p>
 *
 * <p>What <em>is</em> enforced is that the handler which ran actually produced a card token reference. That
 * rules out any method whose handler builds something other than card details, but it cannot rule out a
 * non-card hiding behind a saved-method selection, because {@code OneClickPaymentHandler} builds
 * {@code CardDetails} for every one of them. Closing that needs the stored method's type on the cart; see
 * the follow-ups.</p>
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

		// findConnector rather than getActiveConnector: the missing-connector case is answered here, in the
		// open, instead of arriving as an exception that would have to be caught around the cart inspection
		// too - and a catch that wide is exactly how the fail-closed behaviour below gets lost.
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
			// cannot fund renewals, not a bug, and it happens while the /payments request is being built - the
			// last moment before the money moves. Everything above flattens an unrecognised failure here into a
			// generic authorization error, which is how a legitimate "pick a card" ends up looking like a broken
			// storefront.
			throw new RecurringContractHelper.TokenizationNotSupportedException(
					"Payment method '" + StringUtils.defaultString(paymentMethod, "<missing>")
							+ "' cannot leave a reusable token behind, which " + connector.platform()
							+ " subscriptions require; the shopper has to pay with a card");
		}

		RecurringContractHelper.applySubscriptionContract(paymentRequest);
	}

	/**
	 * Classifies <em>every</em> entry, even once a subscription product has been found. Stopping at the first
	 * match would be cheaper — one is already enough to make the payment need a token — but it would make this
	 * disagree with the activator, which has to look at all of them. The disagreement is not theoretical: for a
	 * cart holding one mapped product and one the rule cannot classify, stopping early makes the outcome depend
	 * on the order of the entries. Mapped first and the payment goes out tokenized, then the activator refuses
	 * the whole order and dead-letters it after its retries; undecidable first and the checkout is refused
	 * before anything is charged. Same cart, same fault, opposite result. Scanning everything means an
	 * undecidable entry is refused here whichever position it sits in.
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
				// AdyenPaymentRequestDecorator and carries no checked exception. The cause is kept: the reason
				// the resolver could not answer is the only thing that makes this diagnosable.
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
		// The prefix alone is not enough: it marks any saved method, not a saved card. Require that the handler
		// which ran turned it into a card token reference. Asked of RecurringContractHelper rather than
		// answered again here: the same predicate decides storePaymentMethod one step later, and two copies
		// would only have to agree forever.
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
