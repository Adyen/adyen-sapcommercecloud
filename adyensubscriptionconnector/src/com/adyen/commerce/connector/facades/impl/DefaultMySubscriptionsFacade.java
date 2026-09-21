/*
 *                        ######
 *                        ######
 *  ############    ####( ######  #####. ######  ############   ############
 *  #############  #####( ######  #####. ######  #####  ######  #####  ######
 *  ###### ######  #####( ######  #####. ######  #####  #####   #####  ######
 *  ###### ######  #####( ######  #####. ######  #####          #####  ######
 *  #############  #############  #############  #############  #####  ######
 *   ############   ############  #############   ############  #####  ######
 *                                       ######
 *                                #############
 *                                ############
 *
 *  Adyen Hybris Extension
 *
 *  Copyright (c) 2026 Adyen B.V.
 *  This file is open source and available under the MIT license.
 *  See the LICENSE file for more info.
 */
package com.adyen.commerce.connector.facades.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adyen.commerce.connector.activation.BillingActivationAttemptService;
import com.adyen.commerce.connector.context.SubscriptionBaseStoreSelectorStrategy;
import com.adyen.commerce.connector.dto.CancelReason;
import com.adyen.commerce.connector.dto.PaymentMethodChangeSupport;
import com.adyen.commerce.connector.dto.PaymentMethodChoice;
import com.adyen.commerce.connector.dto.PaymentMethodEnrollmentPage;
import com.adyen.commerce.connector.dto.PaymentMethodEnrollmentSupport;
import com.adyen.commerce.connector.dto.PaymentMethodSource;
import com.adyen.commerce.connector.dto.PlatformPaymentMethod;
import com.adyen.commerce.connector.dto.PaymentMethodChangeScope;
import com.adyen.commerce.connector.dto.NormalizedSubscriptionStatus;
import com.adyen.commerce.connector.dto.SubscriptionCancellation;
import com.adyen.commerce.connector.facades.MySubscriptionsFacade;
import com.adyen.commerce.connector.facades.data.PaymentMethodChangeResult;
import com.adyen.commerce.connector.facades.data.PaymentMethodChangeReport;
import com.adyen.commerce.connector.facades.data.SubscriptionDisplayState;
import com.adyen.commerce.connector.facades.data.SubscriptionEntryData;
import com.adyen.commerce.connector.facades.data.SubscriptionOverviewData;
import com.adyen.commerce.connector.model.BillingActivationAttemptModel;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;
import com.adyen.commerce.connector.dto.AdyenTokenHandle;
import com.adyen.commerce.connector.dto.CardMetadata;
import com.adyen.commerce.connector.registry.SubscriptionBillingConnectorRegistry;
import com.adyen.commerce.connector.token.AdyenTokenHandleFactory;
import com.adyen.commerce.facades.AdyenStoredCardsFacade;
import com.adyen.commerce.services.AdyenStoredCardAuthorisationService;
import com.adyen.model.checkout.StoredPaymentMethodResource;
import com.adyen.commerce.connector.service.SubscriptionBillingService;

import de.hybris.platform.basecommerce.model.site.BaseSiteModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.payment.PaymentInfoModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.product.ProductService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.session.SessionExecutionBody;
import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.servicelayer.user.UserService;
import de.hybris.platform.site.BaseSiteService;
import de.hybris.platform.store.BaseStoreModel;

/**
 * Default implementation.
 *
 * <p>Ownership is enforced by predicate in the query, never by loading a row and comparing its customer
 * afterwards; the customer always comes from the session and is never accepted as an argument.</p>
 *
 * <p>Every operation runs inside the order's own store. The connectors read their API key, site and gateway
 * account from the base store in the session, and {@code Customer} is global across stores in SAP, so a
 * shopper signed in to one storefront can hold subscriptions bought on another.</p>
 */
public class DefaultMySubscriptionsFacade implements MySubscriptionsFacade
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultMySubscriptionsFacade.class);

	private UserService userService;
	private FlexibleSearchService flexibleSearchService;
	private ProductService productService;
	private SubscriptionBillingService subscriptionBillingService;
	private SessionService sessionService;
	private BaseSiteService baseSiteService;
	private SubscriptionBillingConnectorRegistry connectorRegistry;
	private AdyenTokenHandleFactory tokenHandleFactory;
	private AdyenStoredCardsFacade storedCardsFacade;
	private AdyenStoredCardAuthorisationService storedCardAuthorisationService;

	@Override
	public SubscriptionOverviewData getSubscriptionsForCurrentCustomer()
	{
		final SubscriptionOverviewData overview = new SubscriptionOverviewData();
		final CustomerModel customer = currentCustomer();
		if (customer == null)
		{
			return overview;
		}

		// A platform's payment methods are per customer, so rows sharing a platform and a customer share one
		// remote lookup instead of one HTTP round trip each.
		final Map<String, List<PlatformPaymentMethod>> methodsByCustomer = new HashMap<>();
		// The Adyen vault is per shopper, not per row, and reading it is a remote call: fetched at most
		// once for the whole page, and only if some row turns out to accept a vaulted card.
		final Map<String, List<StoredPaymentMethodResource>> vaultOnce = new HashMap<>();
		final List<SubscriptionEntryData> entries = new ArrayList<>();
		final List<BillingSubscriptionRefModel> refs = findSubscriptions(customer);
		for (final BillingSubscriptionRefModel ref : refs)
		{
			entries.add(toEntry(ref, methodsByCustomer, vaultOnce));
		}
		overview.setSubscriptions(entries);
		overview.setOrdersAwaitingSetup(findOrdersAwaitingSetup(customer));
		applyPaymentMethodEnrollmentOffer(overview, refs);
		// Needs the references, not the rows: which billing account a row sits under is not on screen.
		markRowsThatCouldShareOneCard(overview, refs);
		// Reads the entries just built rather than querying again: the offer has to describe the rows the
		// shopper is looking at.
		applyPaymentMethodChangeOffer(overview);
		return overview;
	}

	@Override
	public String paymentMethodEnrollmentUrlForCurrentCustomer(final String subscriptionCode)
	{
		final CustomerModel customer = currentCustomer();
		if (customer == null || StringUtils.isBlank(subscriptionCode))
		{
			return null;
		}

		// Resolved from the shopper's own subscriptions, so a code they do not own yields nothing rather
		// than a link into somebody else's billing account.
		final BillingSubscriptionRefModel ref = findOwnSubscription(customer, subscriptionCode);
		if (ref == null)
		{
			return null;
		}

		try
		{
			return inStoreContext(ref, () -> subscriptionBillingService.paymentMethodEnrollmentPage(ref))
					.map(PaymentMethodEnrollmentPage::url)
					.orElse(null);
		}
		catch (final RuntimeException e)
		{
			// Deliberately without the exception's message: on platforms whose page is reached by a
			// credential in the URL, a failure while building it can carry that credential.
			LOG.warn("Could not build the payment-method page for platform {} and subscription '{}': {}",
					ref.getPlatform(), subscriptionCode, e.getClass().getName());
			return null;
		}
	}

	@Override
	public boolean cancelForCurrentCustomer(final String code)
	{
		final CustomerModel customer = currentCustomer();
		if (customer == null || StringUtils.isBlank(code))
		{
			return false;
		}

		final BillingSubscriptionRefModel ref = findOwnSubscription(customer, code);
		if (ref == null)
		{
			// An unknown code and somebody else's code are reported identically, so nothing here tells a
			// caller probing for codes which ones exist.
			LOG.info("No subscription matching the requested code belongs to the current customer; refusing.");
			return false;
		}

		// Re-derived rather than trusted from the form: the page that offered the button may be minutes old,
		// and the subscription may have ended since.
		if (!displayState(ref).isCancellable())
		{
			LOG.info("Subscription '{}' is not in a state that can be cancelled by the shopper; refusing.", code);
			return false;
		}
		// A reference with no originating store has no credentials to reach its platform with.
		if (storeOf(ref) == null)
		{
			LOG.warn("Subscription '{}' has no originating store, so its platform cannot be reached; "
					+ "refusing to cancel.", code);
			return false;
		}

		try
		{
			cancelInStoreContext(ref);
			return true;
		}
		catch (final RuntimeException e)
		{
			// The platform's own error text stays in the log rather than reaching the shopper.
			LOG.error("Could not cancel subscription '{}' for the current customer.", code, e);
			return false;
		}
	}

	@Override
	public PaymentMethodChangeResult changePaymentMethodForCurrentCustomer(final String subscriptionCode,
			final String storedPaymentMethodId)
	{
		final CustomerModel customer = currentCustomer();
		if (customer == null)
		{
			return PaymentMethodChangeResult.FAILED;
		}
		if (StringUtils.isBlank(subscriptionCode) || StringUtils.isBlank(storedPaymentMethodId))
		{
			LOG.warn("Refusing a payment-method change with an incomplete request: subscription code {}, "
					+ "stored payment method {}.",
					StringUtils.isBlank(subscriptionCode) ? "MISSING" : "present",
					StringUtils.isBlank(storedPaymentMethodId) ? "MISSING" : "present");
			return PaymentMethodChangeResult.FAILED;
		}

		final BillingSubscriptionRefModel ref = findOwnSubscription(customer, subscriptionCode);
		if (ref == null)
		{
			LOG.info("No subscription matching the requested code belongs to the current customer; refusing.");
			return PaymentMethodChangeResult.FAILED;
		}
		// Re-derived rather than trusted from the form: the page that offered the control may be minutes
		// old, and a row that has ended accepts nothing.
		if (!displayState(ref).isPaymentMethodChangeable())
		{
			LOG.info("Subscription '{}' is not in a state where changing the payment method could achieve "
					+ "anything; refusing.", subscriptionCode);
			return PaymentMethodChangeResult.FAILED;
		}
		// Checked before the capability: asking a connector what it supports needs the row's store in
		// context, so a row without one cannot be asked at all.
		if (storeOf(ref) == null)
		{
			LOG.warn("Subscription '{}' has no originating store, so its platform cannot be reached; "
					+ "refusing to change the payment method.", subscriptionCode);
			return PaymentMethodChangeResult.FAILED;
		}
		final PaymentMethodChangeSupport support = declaredSupportFor(ref);
		if (!support.isSupported())
		{
			LOG.info("The billing platform behind subscription '{}' does not offer a payment-method change.",
					subscriptionCode);
			return PaymentMethodChangeResult.NOT_SUPPORTED_HERE;
		}

		try
		{
			final PaymentMethodChangeScope applied = changeInStoreContext(customer, ref, support,
					storedPaymentMethodId);
			return applied == PaymentMethodChangeScope.CUSTOMER
					? PaymentMethodChangeResult.CHANGED_ALL_SUBSCRIPTIONS
					: PaymentMethodChangeResult.CHANGED_THIS_SUBSCRIPTION;
		}
		catch (final TokenNotOwnedException e)
		{
			// Not an error: a request naming a token this shopper does not have is refused the same way as
			// a subscription that is not theirs. The vault listing is the authority.
			LOG.info("The requested stored payment method is not on the current shopper's vault listing; "
					+ "refusing to change the payment method for subscription '{}'.", subscriptionCode);
			return PaymentMethodChangeResult.FAILED;
		}
		catch (final RuntimeException e)
		{
			LOG.error("Could not change the payment method for subscription '{}'.", subscriptionCode, e);
			return PaymentMethodChangeResult.FAILED;
		}
	}

	@Override
	public PaymentMethodChangeReport changePaymentMethodForAllSubscriptions(final String subscriptionCode,
			final String storedPaymentMethodId)
	{
		// The row the shopper named goes through the single-subscription path first, with every guard that
		// path applies, and nothing fans out until it has succeeded. Siblings are guarded separately, in
		// siblingsOf and in the change itself - a guard added to one side is not inherited by the other.
		final PaymentMethodChangeResult first =
				changePaymentMethodForCurrentCustomer(subscriptionCode, storedPaymentMethodId);
		if (first != PaymentMethodChangeResult.CHANGED_THIS_SUBSCRIPTION)
		{
			// A customer-scoped platform already moved everything, and a refusal has nothing to fan out.
			return PaymentMethodChangeReport.of(first);
		}

		final CustomerModel customer = currentCustomer();
		final BillingSubscriptionRefModel named = findOwnSubscription(customer, subscriptionCode);
		if (named == null)
		{
			return PaymentMethodChangeReport.of(first);
		}

		int moved = 1;
		int failed = 0;
		for (final BillingSubscriptionRefModel other : siblingsOf(customer, named))
		{
			if (movePaymentMethod(customer, other, storedPaymentMethodId))
			{
				moved++;
			}
			else
			{
				failed++;
			}
		}

		final PaymentMethodChangeResult result = failed == 0 && moved > 1
				? PaymentMethodChangeResult.CHANGED_ALL_SUBSCRIPTIONS
				: PaymentMethodChangeResult.CHANGED_THIS_SUBSCRIPTION;
		if (failed > 0)
		{
			LOG.warn("Put one card behind {} of this shopper's subscriptions on platform {}; {} could not "
					+ "be moved.", Integer.valueOf(moved), named.getPlatform(), Integer.valueOf(failed));
		}
		return new PaymentMethodChangeReport(result, moved, failed);
	}

	/**
	 * The shopper's other subscriptions that the same card could actually be put behind.
	 *
	 * <p>Same store and same platform. The store is what decides it: credentials, site and the whole
	 * payment-method namespace hang off the store's config, while {@code externalCustomerId} is minted
	 * from the shopper's global SAP id and is therefore the same string on every site of a platform - it
	 * identifies the shopper, not the account. Comparing it alone would send a card into another site's
	 * account under another store's credentials.</p>
	 *
	 * <p>Rows that could not be acted on anyway are left out here rather than counted as failures. Each
	 * surviving row is still re-checked individually inside the loop, at the cost of a listing call per
	 * row: ownership is worth re-establishing per subscription, and the group is small by nature.</p>
	 */
	protected List<BillingSubscriptionRefModel> siblingsOf(final CustomerModel customer,
			final BillingSubscriptionRefModel named)
	{
		final List<BillingSubscriptionRefModel> siblings = new ArrayList<>();
		for (final BillingSubscriptionRefModel other : findSubscriptions(customer))
		{
			// Identified by what the platform calls it, not by PK: that is the identity the change acts on,
			// and it is the one a row must have before it is changeable at all.
			if (StringUtils.equals(other.getExternalSubscriptionId(), named.getExternalSubscriptionId())
					|| other.getPlatform() != named.getPlatform()
					|| !StringUtils.equals(other.getExternalCustomerId(), named.getExternalCustomerId())
					|| !Objects.equals(storeOf(other), storeOf(named))
					|| storeOf(other) == null
					|| !displayState(other).isPaymentMethodChangeable()
					// Its own store's configuration decides, and two stores selling on one platform need
					// not agree. A row nothing would have offered is left out rather than counted as a
					// failure the shopper is then told about.
					|| !declaredSupportFor(other).isSupported())
			{
				continue;
			}
			siblings.add(other);
		}
		return siblings;
	}

	/**
	 * What makes two rows share one payment-method namespace: the store that selects the platform account,
	 * the platform, and the customer reference under it. The same key the fan-out groups on, so the offer
	 * and what the offer does cannot drift apart.
	 */
	protected String accountKeyOf(final BillingSubscriptionRefModel ref)
	{
		final BaseStoreModel store = storeOf(ref);
		return (store == null ? "no-store" : store.getUid()) + "/" + ref.getPlatform().getCode()
				+ "/" + ref.getExternalCustomerId();
	}

	/**
	 * One subscription of the fan-out. Answers only whether it moved: a platform refusing this row says
	 * nothing about the others, so a failure here is counted rather than thrown.
	 */
	protected boolean movePaymentMethod(final CustomerModel customer,
			final BillingSubscriptionRefModel ref, final String storedPaymentMethodId)
	{
		try
		{
			changeInStoreContext(customer, ref, declaredSupportFor(ref), storedPaymentMethodId);
			return true;
		}
		catch (final RuntimeException e)
		{
			LOG.warn("Could not also move subscription '{}' onto the chosen payment method.", ref.getCode(), e);
			return false;
		}
	}

	/**
	 * What the connector behind this row says a payment-method change would move.
	 *
	 * <p>Resolved from the row's own platform, never from the store's active one: after a store migrates
	 * from one platform to another its old subscriptions still live on the old one. A row whose adapter is
	 * not deployed answers {@code NOT_SUPPORTED} rather than taking the page down with it.</p>
	 */
	protected PaymentMethodChangeSupport declaredSupportFor(final BillingSubscriptionRefModel ref)
	{
		try
		{
			// In the row's own store, not the session's: Recurly reads its capability from two switches on
			// the base store, and a shopper is global across shops in SAP, so they can hold subscriptions
			// bought in more than one of them.
			return inStoreContext(ref,
					() -> connectorRegistry.getConnector(ref.getPlatform()).capabilities().paymentMethodChange());
		}
		// inStoreContext rewraps checked failures, so a missing connector arrives here as an
		// IllegalStateException around ConnectorNotConfiguredException rather than as itself.
		catch (final RuntimeException e)
		{
			LOG.info("No connector is deployed for platform {}; treating its subscriptions as unchangeable.",
					ref.getPlatform());
			return PaymentMethodChangeSupport.NONE;
		}
	}

	protected PaymentMethodChangeScope declaredScopeFor(final BillingSubscriptionRefModel ref)
	{
		return declaredSupportFor(ref).scope();
	}

	/** What the connector behind this row says its platform's own payment-method page would do. */
	protected PaymentMethodEnrollmentSupport declaredEnrollmentFor(final BillingSubscriptionRefModel ref)
	{
		try
		{
			return inStoreContext(ref,
					() -> connectorRegistry.getConnector(ref.getPlatform()).capabilities().paymentMethodEnrollment());
		}
		catch (final RuntimeException e)
		{
			return PaymentMethodEnrollmentSupport.NONE;
		}
	}

	/**
	 * Decides whether to invite the shopper to their platform's own payment-method page, and against which
	 * row.
	 *
	 * <p>The first row that both carries a public code and sits on a platform offering the page wins, for
	 * the same reason the page-level change control picks that way: the invitation has to name a
	 * subscription the POST can resolve back to a customer and a store.</p>
	 *
	 * <p>Only the URL is withheld until the shopper clicks. Whether to show the invitation is answered from
	 * declared capability alone, so rendering the page mints no credential.</p>
	 */
	protected void applyPaymentMethodEnrollmentOffer(final SubscriptionOverviewData overview,
			final List<BillingSubscriptionRefModel> refs)
	{
		for (final BillingSubscriptionRefModel ref : refs)
		{
			if (StringUtils.isBlank(ref.getCode()))
			{
				continue;
			}
			final PaymentMethodEnrollmentSupport enrollment = declaredEnrollmentFor(ref);
			if (enrollment.isOffered())
			{
				overview.setPaymentMethodEnrollmentEffect(enrollment.effect());
				overview.setPaymentMethodEnrollmentSubscriptionCode(ref.getCode());
				return;
			}
		}
	}

	/**
	 * Raised when the chosen token is not among the cards Adyen holds for this shopper.
	 *
	 * <p>Its own type because the caller has to tell it apart from a failure: "this is not your card" is a
	 * decision and must never be retried, whereas a vault listing that could not be read is transient and
	 * says nothing about ownership.</p>
	 */
	protected static class TokenNotOwnedException extends RuntimeException
	{
		private static final long serialVersionUID = 1L;

		public TokenNotOwnedException(final String message)
		{
			super(message);
		}
	}

	/**
	 * Runs something with the subscription's own store in context, and gives back what it returned.
	 *
	 * <p>The connectors read their credentials, site and gateway account from the base store in the session,
	 * so without the row's own store in context a call would be sent to the wrong billing account.</p>
	 */
	protected <T> T inStoreContext(final BillingSubscriptionRefModel ref, final Callable<T> work)
	{
		final BaseStoreModel store = storeOf(ref);
		if (store == null)
		{
			throw new IllegalStateException("Subscription has no originating store; its connector's "
					+ "credentials cannot be selected safely");
		}

		final Map<String, Object> sessionParameters = Collections.singletonMap(
				SubscriptionBaseStoreSelectorStrategy.CURRENT_SUBSCRIPTION_BASE_STORE, store);
		@SuppressWarnings("unchecked")
		final T result = (T) sessionService.executeInLocalViewWithParams(sessionParameters,
				new SessionExecutionBody()
				{
					@Override
					public Object execute()
					{
						try
						{
							return work.call();
						}
						catch (final RuntimeException e)
						{
							throw e;
						}
						catch (final Exception e)
						{
							throw new IllegalStateException(e);
						}
					}
				});
		return result;
	}

	/**
	 * Performs the change with the subscription's own store in context, and answers what it moved.
	 *
	 * <p>Through {@code SubscriptionBillingService} rather than the connector registry directly: the service
	 * is what supplies the idempotency key, validates the merchant account, and records locally which
	 * instrument is now billing.</p>
	 */
	protected PaymentMethodChangeScope changeInStoreContext(final CustomerModel customer,
			final BillingSubscriptionRefModel ref, final PaymentMethodChangeSupport support,
			final String optionId)
	{
		final PaymentMethodChangeScope applied = inStoreContext(ref, () ->
		{
			// Inside the local view on purpose: reading the shopper's vault and listing what the platform
			// holds are both answered against the store in context, which must be the subscription's.
			final PaymentMethodChoice choice = choiceFor(customer, ref, support, optionId);
			return subscriptionBillingService.changePaymentMethod(ref, choice).appliedScope();
		});

		if (applied == null)
		{
			// Deliberately not defaulted: the scope decides which sentence the shopper reads, so guessing
			// at it would state something untrue.
			throw new IllegalStateException("The payment-method change did not report the scope it applied; "
					+ "refusing to describe it rather than guess");
		}
		return applied;
	}

	/** What the platform already holds for this subscription's customer, in that subscription's store. */
	protected List<PlatformPaymentMethod> listPlatformMethods(final BillingSubscriptionRefModel ref)
	{
		return inStoreContext(ref, () -> subscriptionBillingService.listPaymentMethods(ref));
	}

	protected List<PlatformPaymentMethod> platformMethodsFor(final BillingSubscriptionRefModel ref)
	{
		try
		{
			return listPlatformMethods(ref);
		}
		catch (final RuntimeException e)
		{
			LOG.warn("Could not list the payment methods platform {} holds for subscription '{}'; the row "
					+ "will offer no control.", ref.getPlatform(), ref.getCode(), e);
			return List.of();
		}
	}

	/**
	 * Turns what the shopper posted into what the connector accepts, using the connector's own declaration.
	 *
	 * <p>The same opaque option id means an Adyen vault reference on one platform and the platform's own
	 * payment-method id on another, so the connector's declared source decides how to read it. Neither
	 * branch trusts the id: the vaulted one is checked against the shopper's vault listing, the platform
	 * one against the options that row offers.</p>
	 */
	protected PaymentMethodChoice choiceFor(final CustomerModel customer, final BillingSubscriptionRefModel ref,
			final PaymentMethodChangeSupport support, final String optionId)
	{
		if (support.accepts(PaymentMethodSource.ALREADY_ON_PLATFORM))
		{
			// Deliberately not through the render path's forgiving helper: a listing that could not be read
			// says nothing about ownership, so it must surface as a failure rather than as a refusal.
			final List<PlatformPaymentMethod> offered = listPlatformMethods(ref);
			if (offered.stream().anyMatch(method -> optionId.equals(method.id())))
			{
				return new PaymentMethodChoice.AlreadyOnPlatform(optionId);
			}
			// One opaque id, two possible namespaces. An id absent from the platform's own list is only a
			// candidate for the vault when the connector accepts vaulted tokens too; otherwise it is simply
			// not this shopper's.
			if (!support.accepts(PaymentMethodSource.ADYEN_VAULTED_TOKEN))
			{
				throw new TokenNotOwnedException("The chosen payment method is not one this subscription's "
						+ "platform holds for this customer");
			}
		}
		return vaultedChoice(customer, ref, optionId);
	}

	/**
	 * The shopper's Adyen-vaulted cards that this row's platform could actually be pointed at.
	 *
	 * <p>A platform that charges an imported token as a merchant-initiated transaction needs the network
	 * transaction id of the authorisation that vaulted it, and Adyen does not report one for every token.
	 * Such a card is left out rather than offered, because its submission would be refused by the adapter -
	 * the same rule {@code PaymentMethodChangeSupport} applies to sources.</p>
	 */
	protected List<PlatformPaymentMethod> vaultOptionsFor(final BillingSubscriptionRefModel ref,
			final Map<String, List<StoredPaymentMethodResource>> vaultOnce)
	{
		final boolean needsNtid = requiresNetworkTransactionId(ref);
		final List<StoredPaymentMethodResource> vault = vaultOnce.computeIfAbsent("vault", key -> readVault());
		if (vault.isEmpty())
		{
			return List.of();
		}
		final Map<String, String> known = knownNetworkTxReferences(currentCustomer());

		final List<PlatformPaymentMethod> options = new ArrayList<>();
		int withoutNetworkTransactionId = 0;
		for (final StoredPaymentMethodResource card : vault)
		{
			if (StringUtils.isBlank(card.getId()))
			{
				continue;
			}
			if (needsNtid && StringUtils.isBlank(networkTxReferenceOf(card, known)))
			{
				withoutNetworkTransactionId++;
				continue;
			}
			options.add(new PlatformPaymentMethod(card.getId(), vaultLabel(card), cardMetadataOf(card), false));
		}
		// Presence only, never the value: it is a scheme-level payment identifier. The counts are what
		// answers "why is my new card not on the list" without a debugger.
		LOG.info("Adyen vault for subscription '{}': {} card(s), {} offered, {} withheld for carrying no "
				+ "network transaction id (platform requires one: {}).", ref.getCode(),
				Integer.valueOf(vault.size()), Integer.valueOf(options.size()),
				Integer.valueOf(withoutNetworkTransactionId), Boolean.valueOf(needsNtid));
		return options;
	}

	/** The shopper's vault, or nothing: a listing that cannot be read withholds cards rather than failing
	 *  the page, which still renders every subscription and the platform's own methods. */
	protected List<StoredPaymentMethodResource> readVault()
	{
		try
		{
			final List<StoredPaymentMethodResource> vault =
					storedCardsFacade.getStoredCardsPageDataForCurrentCustomer().getStoredCards();
			return vault == null ? List.of() : vault;
		}
		catch (final RuntimeException e)
		{
			LOG.warn("Could not read the shopper's Adyen vault; offering no vaulted cards on this page.", e);
			return List.of();
		}
	}

	/**
	 * The network transaction id for a vaulted card, from whichever source has one.
	 *
	 * <p>The vault listing is asked first because a merchant whose account reports it there needs nothing
	 * kept locally. Otherwise it is what was captured when the card was authorised: Adyen reports the
	 * reference in the authorisation response and not again afterwards, so an unrecorded card has none to
	 * be found anywhere.</p>
	 */
	protected String networkTxReferenceOf(final StoredPaymentMethodResource card,
			final Map<String, String> known)
	{
		final String reported = StringUtils.trimToNull(card.getNetworkTxReference());
		return reported != null ? reported : known.get(card.getId());
	}

	protected Map<String, String> knownNetworkTxReferences(final CustomerModel customer)
	{
		try
		{
			return storedCardAuthorisationService.networkTxReferencesFor(customer);
		}
		catch (final RuntimeException e)
		{
			LOG.warn("Could not read the stored network transaction ids; treating them as unknown.", e);
			return Map.of();
		}
	}

	/**
	 * Which of this row's options is billing it today, as the row's own connector reads its stored
	 * reference. {@code null} when nothing is recorded or the adapter cannot say - the page then marks
	 * nothing rather than marking the wrong one.
	 */
	protected String currentPaymentMethodIdOf(final BillingSubscriptionRefModel ref)
	{
		if (StringUtils.isBlank(ref.getExternalPaymentMethodId()))
		{
			return null;
		}
		try
		{
			return inStoreContext(ref, () -> connectorRegistry.getConnector(ref.getPlatform())
					.listedPaymentMethodId(ref.getExternalPaymentMethodId()));
		}
		catch (final RuntimeException e)
		{
			return null;
		}
	}

	protected boolean requiresNetworkTransactionId(final BillingSubscriptionRefModel ref)
	{
		try
		{
			return inStoreContext(ref, () -> Boolean.valueOf(connectorRegistry.getConnector(ref.getPlatform())
					.capabilities().requiresNetworkTransactionId())).booleanValue();
		}
		catch (final RuntimeException e)
		{
			// Unknown means withhold: offering a card the platform then refuses is worse than offering none.
			return true;
		}
	}

	/** What a vaulted card is called on the page. The vault reports brand and last four separately. */
	protected String vaultLabel(final StoredPaymentMethodResource card)
	{
		final String lastFour = StringUtils.trimToNull(card.getLastFour());
		final String brand = StringUtils.trimToNull(card.getBrand());
		if (lastFour == null)
		{
			return StringUtils.defaultIfBlank(brand, "Saved card");
		}
		return brand == null ? "\u2022\u2022\u2022\u2022 " + lastFour
				: brand + " \u2022\u2022\u2022\u2022 " + lastFour;
	}

	/**
	 * A card from the shopper's Adyen vault, carrying whatever authorisation Adyen still reports for it.
	 *
	 * <p>The network transaction id is read rather than defaulted: platforms that charge an imported token
	 * as a merchant-initiated transaction are refused by their own adapter without one, and that refusal is
	 * the correct outcome for a token Adyen reports none for.</p>
	 */
	protected PaymentMethodChoice vaultedChoice(final CustomerModel customer,
			final BillingSubscriptionRefModel ref, final String optionId)
	{
		final StoredPaymentMethodResource card = ownedCard(customer, optionId);
		final BaseStoreModel store = storeOf(ref);
		try
		{
			return new PaymentMethodChoice.AdyenVaultedToken(tokenHandleFactory.createForVaultedToken(
					customer, store, optionId, networkTxReferenceOf(card, knownNetworkTxReferences(customer)),
					cardMetadataOf(card)));
		}
		catch (final Exception e)
		{
			throw new IllegalStateException(e);
		}
	}

	/**
	 * The shopper's own card with this id, or a refusal.
	 *
	 * <p>This is the access check: the token id arrives as a request parameter, and the listing rendered
	 * into the page is not a control, since a request does not have to come from that page. A listing that
	 * could not be read is <em>not</em> an answer about ownership, so it is raised as a failure rather than
	 * a refusal.</p>
	 */
	protected StoredPaymentMethodResource ownedCard(final CustomerModel customer, final String storedPaymentMethodId)
	{
		final List<StoredPaymentMethodResource> vault;
		try
		{
			vault = storedCardsFacade.getStoredCardsPageDataForCurrentCustomer().getStoredCards();
		}
		catch (final RuntimeException e)
		{
			throw new IllegalStateException("Could not read the shopper's stored cards; refusing to change a "
					+ "payment method without establishing that the chosen one is theirs", e);
		}

		return (vault == null ? Collections.<StoredPaymentMethodResource> emptyList() : vault).stream()
				.filter(stored -> storedPaymentMethodId.equals(stored.getId()))
				.findFirst()
				.orElseThrow(() -> new TokenNotOwnedException(
						"The chosen stored payment method is not among this shopper's vaulted cards"));
	}

	/**
	 * Display metadata for the card the shopper chose.
	 *
	 * <p>Built from the object {@link #ownedCard} already returned rather than looked up again: that lookup
	 * is the access check, and doing it twice invites the two answers to differ.</p>
	 */
	protected CardMetadata cardMetadataOf(final StoredPaymentMethodResource card)
	{
		return card == null ? null
				: new CardMetadata(null, card.getLastFour(), card.getHolderName(),
						expiry(card.getExpiryMonth(), card.getExpiryYear()), null);
	}

	/** Chargebee wants the month and year separately; the handle carries them joined as MM/YYYY. */
	protected String expiry(final String month, final String year)
	{
		return StringUtils.isAnyBlank(month, year) ? null
				: StringUtils.leftPad(month, 2, '0') + "/" + (year.length() == 2 ? "20" + year : year);
	}

	/**
	 * Decides what the page says about changing payment methods, from the rows it is about to show.
	 *
	 * <p>The control above the list is offered only for a {@code CUSTOMER}-scoped row, because that scope
	 * moves every subscription the customer has on that platform; a {@code SUBSCRIPTION}-scoped row carries
	 * its control in the row instead. Whether anything can be changed at all is tracked separately, so the
	 * page can tell "there is no control above the list" apart from "this cannot be done here".</p>
	 */
	protected void applyPaymentMethodChangeOffer(final SubscriptionOverviewData overview)
	{
		int unsupportedPlatform = 0;
		int wrongState = 0;
		int missingCode = 0;

		for (final SubscriptionEntryData entry : overview.getSubscriptions())
		{
			if (entry.getPaymentMethodChangeScope().isSupported())
			{
				// A row that is merely too new to act on says nothing about whether its platform can change
				// cards, so this tracks the platform rather than today's data.
				overview.setPaymentMethodChangeSupportedSomewhere(true);
			}

			if (entry.isPaymentMethodChangeable())
			{
				overview.setAnyPaymentMethodChangeable(true);
				// A subscription-scoped row only becomes a visible control once the platform has something
				// to move to.
				if (entry.getPaymentMethodChangeScope() == PaymentMethodChangeScope.SUBSCRIPTION
						&& !entry.getPaymentMethodOptions().isEmpty())
				{
					overview.setAnyRowPaymentMethodControl(true);
				}
				// The first customer-scoped row wins the page-level control; any of them identifies the
				// customer and the platform, which is all that scope needs.
				if (entry.getPaymentMethodChangeScope() == PaymentMethodChangeScope.CUSTOMER
						&& overview.getPaymentMethodSubscriptionCode() == null)
				{
					overview.setPaymentMethodChangeScope(PaymentMethodChangeScope.CUSTOMER);
					overview.setPaymentMethodSubscriptionCode(entry.getCode());
				}
				continue;
			}

			if (!entry.getPaymentMethodChangeScope().isSupported())
			{
				unsupportedPlatform++;
			}
			else if (entry.getState() == null || !entry.getState().isPaymentMethodChangeable())
			{
				wrongState++;
			}
			else
			{
				missingCode++;
			}
		}

		markRowsCoveredByThePageControl(overview);
		reportWhatWasNotOffered(overview, unsupportedPlatform, wrongState, missingCode);
	}

	/**
	 * Marks which rows a control on this page will actually move.
	 *
	 * <p>A row the shopper can act on directly is covered by definition. So is any customer-scoped row once
	 * the control above the list exists, <em>whether or not that row could be named in a form itself</em>:
	 * the change is sent at the platform's customer, and every unpinned subscription of theirs moves with
	 * it.</p>
	 */
	protected void markRowsCoveredByThePageControl(final SubscriptionOverviewData overview)
	{
		final boolean pageControlOffered = overview.getPaymentMethodSubscriptionCode() != null;
		for (final SubscriptionEntryData entry : overview.getSubscriptions())
		{
			entry.setPaymentMethodChangeCovered(entry.isPaymentMethodChangeable()
					|| (pageControlOffered
							&& entry.getPaymentMethodChangeScope() == PaymentMethodChangeScope.CUSTOMER));
		}
	}

	/**
	 * Marks the rows whose control may also offer to move the shopper's other subscriptions.
	 *
	 * <p>Only rows a platform pins individually: where the change is already customer-scoped the offer
	 * would be a second name for what the control does anyway. Counted over the page first, because a lone
	 * subscription has nothing to share a card with.</p>
	 */
	protected void markRowsThatCouldShareOneCard(final SubscriptionOverviewData overview,
			final List<BillingSubscriptionRefModel> refs)
	{
		// Grouped exactly as the fan-out groups, so the offer is never made to a row it would move nothing
		// for.
		final Map<String, List<SubscriptionEntryData>> byAccount = new HashMap<>();
		final List<SubscriptionEntryData> entries = overview.getSubscriptions();
		for (int i = 0; i < entries.size() && i < refs.size(); i++)
		{
			final SubscriptionEntryData entry = entries.get(i);
			if (!entry.isPaymentMethodChangeable()
					|| entry.getPaymentMethodChangeScope() != PaymentMethodChangeScope.SUBSCRIPTION)
			{
				continue;
			}
			byAccount.computeIfAbsent(accountKeyOf(refs.get(i)), key -> new ArrayList<>()).add(entry);
		}
		byAccount.values().stream()
				.filter(group -> group.size() > 1)
				.flatMap(List::stream)
				.forEach(entry -> entry.setPaymentMethodShareable(true));
	}

	/**
	 * Writes down every way of offering nothing.
	 *
	 * <p>The missing-identifier case is a data gap with a known remedy, so the warning names it. It is
	 * emitted even when the page did offer something: rows left out while others work look like nothing is
	 * wrong on screen.</p>
	 */
	protected void reportWhatWasNotOffered(final SubscriptionOverviewData overview, final int unsupportedPlatform,
			final int wrongState, final int missingCode)
	{
		if (missingCode > 0)
		{
			LOG.warn("{} of this shopper's subscription(s) are on a platform that supports a payment-method "
					+ "change and in a state that allows it, but carry no public code. Run a system update "
					+ "with essential data for the subscription connector, which assigns one to every "
					+ "reference predating the column.", Integer.valueOf(missingCode));
		}
		if (!overview.isAnyPaymentMethodChangeable() && (unsupportedPlatform > 0 || wrongState > 0))
		{
			// INFO because this is the feature working; the counts answer "why is there no control" without
			// a debugger.
			LOG.info("Not offering a payment-method change: {} subscription(s) are on a platform that does "
					+ "not support it, {} are in a state where it would achieve nothing.",
					Integer.valueOf(unsupportedPlatform), Integer.valueOf(wrongState));
		}
	}

	// --- reading ---

	protected List<BillingSubscriptionRefModel> findSubscriptions(final CustomerModel customer)
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery(
				"SELECT {pk} FROM {BillingSubscriptionRef} WHERE {customer} = ?customer "
						+ "ORDER BY {currentPeriodEnd} DESC, {pk} DESC");
		query.addQueryParameter("customer", customer);
		return flexibleSearchService.<BillingSubscriptionRefModel> search(query).getResult();
	}

	/**
	 * The one subscription with this code that belongs to this customer, or {@code null}.
	 *
	 * <p>Both conditions are in the query on purpose — see the class javadoc.</p>
	 */
	protected BillingSubscriptionRefModel findOwnSubscription(final CustomerModel customer, final String code)
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery(
				"SELECT {pk} FROM {BillingSubscriptionRef} WHERE {customer} = ?customer AND {code} = ?code");
		query.addQueryParameter("customer", customer);
		query.addQueryParameter("code", code);
		final List<BillingSubscriptionRefModel> result = flexibleSearchService
				.<BillingSubscriptionRefModel> search(query).getResult();
		return result.isEmpty() ? null : result.get(0);
	}

	/**
	 * Orders this shopper paid for whose subscription was given up on.
	 *
	 * <p>Only the dead letter, not every failed attempt: an attempt still being retried is expected to
	 * succeed shortly.</p>
	 */
	protected List<String> findOrdersAwaitingSetup(final CustomerModel customer)
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery(
				"SELECT {a.pk} FROM {BillingActivationAttempt AS a JOIN Order AS o ON {a.order} = {o.pk}} "
						+ "WHERE {o.user} = ?customer AND {a.status} = ?status ORDER BY {a.lastAttemptAt} DESC");
		query.addQueryParameter("customer", customer);
		query.addQueryParameter("status", BillingActivationAttemptService.STATUS_DEAD_LETTER);

		final List<String> orderCodes = new ArrayList<>();
		for (final BillingActivationAttemptModel attempt : flexibleSearchService
				.<BillingActivationAttemptModel> search(query).getResult())
		{
			final AbstractOrderModel order = attempt.getOrder();
			if (order != null && StringUtils.isNotBlank(order.getCode()))
			{
				orderCodes.add(order.getCode());
			}
		}
		return orderCodes;
	}

	// --- mapping ---

	protected SubscriptionEntryData toEntry(final BillingSubscriptionRefModel ref)
	{
		// Caches of its own, so a single-row caller need not know they exist.
		return toEntry(ref, new HashMap<>());
	}

	protected SubscriptionEntryData toEntry(final BillingSubscriptionRefModel ref,
			final Map<String, List<PlatformPaymentMethod>> methodsByCustomer)
	{
		return toEntry(ref, methodsByCustomer, new HashMap<>());
	}

	protected SubscriptionEntryData toEntry(final BillingSubscriptionRefModel ref,
			final Map<String, List<PlatformPaymentMethod>> methodsByCustomer,
			final Map<String, List<StoredPaymentMethodResource>> vaultOnce)
	{
		final SubscriptionEntryData entry = new SubscriptionEntryData();
		entry.setCode(ref.getCode());
		entry.setProductName(displayName(ref));
		final SubscriptionDisplayState state = displayState(ref);
		entry.setState(state);
		entry.setEffectiveDate(effectiveDate(ref, state));
		entry.setQuantity(ref.getQuantity());

		// Every action is sent to the row's own platform with the originating store's credentials, so a
		// reference with no store can be described but not acted on. Both buttons hang off this.
		final boolean manageable = storeOf(ref) != null;
		entry.setManageable(manageable);

		final PaymentMethodChangeSupport support = declaredSupportFor(ref);
		final PaymentMethodChangeScope scope = support.scope();
		entry.setPaymentMethodChangeScope(scope);
		entry.setPaymentMethodChangeable(manageable && scope.isSupported()
				&& state.isPaymentMethodChangeable() && StringUtils.isNotBlank(ref.getCode()));

		// Only for a row that will carry its own control: this is a remote call to the billing platform,
		// not something to make behind every row of every page view.
		if (entry.isPaymentMethodChangeable() && scope == PaymentMethodChangeScope.SUBSCRIPTION)
		{
			final List<PlatformPaymentMethod> offered = methodsByCustomer.computeIfAbsent(
					ref.getPlatform().getCode() + "/" + ref.getExternalCustomerId(),
					key -> platformMethodsFor(ref));
			// Fewer than two is not a choice: the only thing on the list is what is already billing. Most
			// accounts hold exactly one, so this is the common case rather than an edge.
			entry.setPaymentMethodOptions(offered.size() < 2 ? List.of() : offered);
			entry.setCurrentPaymentMethodId(currentPaymentMethodIdOf(ref));
		}

		if (entry.isPaymentMethodChangeable() && support.accepts(PaymentMethodSource.ADYEN_VAULTED_TOKEN))
		{
			entry.setAdyenVaultOptions(vaultOptionsFor(ref, vaultOnce));
		}

		final AbstractOrderModel order = ref.getOrder();
		if (order != null)
		{
			entry.setOrderCode(order.getCode());
			entry.setOrderDate(order.getDate());
			entry.setPaymentMethodSummary(cardSummary(order.getPaymentInfo()));
		}
		return entry;
	}

	/**
	 * What the shopper reads instead of a plan code.
	 *
	 * <p>{@code getProductForCode} throws rather than returning null when the product is gone, and throws a
	 * different exception again when two catalogue versions are visible at once, so the fallback belongs in
	 * a catch. A subscription outlives the catalogue entry it was sold from and must stay listed and
	 * cancellable.</p>
	 */
	protected String displayName(final BillingSubscriptionRefModel ref)
	{
		final String productCode = ref.getProductCode();
		if (StringUtils.isBlank(productCode))
		{
			return null;
		}
		try
		{
			final ProductModel product = productService.getProductForCode(productCode);
			return StringUtils.defaultIfBlank(product.getName(), productCode);
		}
		catch (final RuntimeException e)
		{
			LOG.debug("Product '{}' could not be resolved for a subscription listing; showing the code.",
					productCode, e);
			return productCode;
		}
	}

	/**
	 * The single place that decides what a shopper is told about one subscription.
	 *
	 * <p>The first question is not the status but whether any platform has ever confirmed this row.
	 * {@code platformUpdatedAt} answers it because only a reconciliation writes it; {@code lastSyncedAt}
	 * would not, since it is deliberately cleared to hurry the sweep along after a cancellation whose
	 * follow-up read failed.</p>
	 */
	protected SubscriptionDisplayState displayState(final BillingSubscriptionRefModel ref)
	{
		if (ref.getPlatformUpdatedAt() == null)
		{
			return SubscriptionDisplayState.SETTING_UP;
		}

		final boolean ending = Boolean.TRUE.equals(ref.getCancelAtPeriodEnd());
		final NormalizedSubscriptionStatus status = normalizedStatus(ref.getStatus());
		if (status == null)
		{
			return SubscriptionDisplayState.UNAVAILABLE;
		}
		return switch (status)
		{
			case ACTIVE -> ending ? SubscriptionDisplayState.ENDING : SubscriptionDisplayState.ACTIVE;
			// Dunning outranks the pending end on both adapters, so this pair has to exist.
			case PAST_DUE -> ending ? SubscriptionDisplayState.PAST_DUE_ENDING : SubscriptionDisplayState.PAST_DUE;
			case PENDING -> startsInTheFuture(ref)
					? SubscriptionDisplayState.STARTING_SOON
					: SubscriptionDisplayState.SETTING_UP;
			case PAUSED -> SubscriptionDisplayState.PAUSED;
			case CANCELLED, EXPIRED, FAILED -> SubscriptionDisplayState.ENDED;
			case UNKNOWN -> SubscriptionDisplayState.UNAVAILABLE;
		};
	}

	protected Date effectiveDate(final BillingSubscriptionRefModel ref, final SubscriptionDisplayState state)
	{
		return switch (state)
		{
			case ACTIVE, ENDING, PAST_DUE, PAST_DUE_ENDING, ENDED -> ref.getCurrentPeriodEnd();
			case STARTING_SOON -> ref.getCurrentPeriodStart();
			case SETTING_UP, PAUSED, UNAVAILABLE -> null;
		};
	}

	protected boolean startsInTheFuture(final BillingSubscriptionRefModel ref)
	{
		final Date start = ref.getCurrentPeriodStart();
		return start != null && start.after(new Date());
	}

	/**
	 * The stored status as a normalized value, or {@code null} when the literal is not one.
	 *
	 * <p>The column is a string, so it can hold a value this vocabulary does not have — from an older
	 * release, or from a platform that grew an unmapped state. {@code null} sends the row to
	 * {@code UNAVAILABLE}, which says nothing and offers nothing.</p>
	 */
	protected NormalizedSubscriptionStatus normalizedStatus(final String stored)
	{
		if (StringUtils.isBlank(stored))
		{
			return null;
		}
		try
		{
			return NormalizedSubscriptionStatus.valueOf(stored);
		}
		catch (final IllegalArgumentException e)
		{
			LOG.warn("Subscription carries the unrecognised status literal '{}'; showing it as unavailable.", stored);
			return null;
		}
	}

	protected String cardSummary(final PaymentInfoModel paymentInfo)
	{
		return paymentInfo == null ? null : paymentInfo.getAdyenCardSummary();
	}

	// --- writing ---

	protected void cancelInStoreContext(final BillingSubscriptionRefModel ref)
	{
		final BaseStoreModel store = storeOf(ref);
		if (store == null)
		{
			throw new IllegalStateException("Subscription has no originating store; its connector's "
					+ "credentials cannot be selected safely");
		}

		final Map<String, Object> sessionParameters = Collections.singletonMap(
				SubscriptionBaseStoreSelectorStrategy.CURRENT_SUBSCRIPTION_BASE_STORE, store);
		sessionService.executeInLocalViewWithParams(sessionParameters, new SessionExecutionBody()
		{
			@Override
			public void executeWithoutResult()
			{
				final AbstractOrderModel order = ref.getOrder();
				final BaseSiteModel site = order == null ? null : order.getSite();
				if (site != null)
				{
					// false: catalogue versions are not needed to read a store's connector credentials, and
					// activating them is the expensive half of this call.
					baseSiteService.setCurrentBaseSite(site, false);
				}
				try
				{
					subscriptionBillingService.cancel(ref,
							SubscriptionCancellation.endOfPeriod(CancelReason.REQUESTED_BY_CUSTOMER));
				}
				catch (final Exception e)
				{
					// Rewrapped because SessionExecutionBody cannot throw a checked exception.
					throw new IllegalStateException(e);
				}
			}
		});
	}

	protected BaseStoreModel storeOf(final BillingSubscriptionRefModel ref)
	{
		final AbstractOrderModel order = ref.getOrder();
		return order == null ? null : order.getStore();
	}

	/**
	 * The signed-in customer, or {@code null} for anyone else.
	 *
	 * <p>A guest never reaches this page: the accelerator confines {@code /my-account/**} to
	 * {@code ROLE_CUSTOMERGROUP}, and activation refuses to start a subscription for a guest at all,
	 * precisely because this page is the only way to stop one.</p>
	 */
	protected CustomerModel currentCustomer()
	{
		if (userService.isAnonymousUser(userService.getCurrentUser()))
		{
			return null;
		}
		return userService.getCurrentUser() instanceof CustomerModel customer ? customer : null;
	}

	public void setUserService(final UserService userService)
	{
		this.userService = userService;
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}

	public void setProductService(final ProductService productService)
	{
		this.productService = productService;
	}

	public void setSubscriptionBillingService(final SubscriptionBillingService subscriptionBillingService)
	{
		this.subscriptionBillingService = subscriptionBillingService;
	}

	public void setSessionService(final SessionService sessionService)
	{
		this.sessionService = sessionService;
	}

	public void setBaseSiteService(final BaseSiteService baseSiteService)
	{
		this.baseSiteService = baseSiteService;
	}

	public void setConnectorRegistry(final SubscriptionBillingConnectorRegistry connectorRegistry)
	{
		this.connectorRegistry = connectorRegistry;
	}

	public void setTokenHandleFactory(final AdyenTokenHandleFactory tokenHandleFactory)
	{
		this.tokenHandleFactory = tokenHandleFactory;
	}

	public void setStoredCardAuthorisationService(
			final AdyenStoredCardAuthorisationService storedCardAuthorisationService)
	{
		this.storedCardAuthorisationService = storedCardAuthorisationService;
	}

	public void setStoredCardsFacade(final AdyenStoredCardsFacade storedCardsFacade)
	{
		this.storedCardsFacade = storedCardsFacade;
	}
}
