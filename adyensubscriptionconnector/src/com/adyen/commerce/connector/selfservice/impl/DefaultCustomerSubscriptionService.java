/*
 *                        ######
 *                        ######
 *  ############    ####( ######  #####. ######  ############   ############
 *  #############  #####( ######  #####. ######  #############  #############
 *         ######  #####( ######  #####. ######  #####  ######  #####  ######
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
package com.adyen.commerce.connector.selfservice.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adyen.commerce.connector.activation.BillingActivationAttemptService;
import com.adyen.commerce.connector.context.SubscriptionStoreContext;
import com.adyen.commerce.connector.dto.CancelReason;
import com.adyen.commerce.connector.dto.CardMetadata;
import com.adyen.commerce.connector.dto.NormalizedSubscriptionStatus;
import com.adyen.commerce.connector.dto.PaymentMethodChangeScope;
import com.adyen.commerce.connector.dto.PaymentMethodChangeSupport;
import com.adyen.commerce.connector.dto.PaymentMethodChoice;
import com.adyen.commerce.connector.dto.PaymentMethodEnrollmentPage;
import com.adyen.commerce.connector.dto.PaymentMethodEnrollmentSupport;
import com.adyen.commerce.connector.dto.PaymentMethodSource;
import com.adyen.commerce.connector.dto.PlatformPaymentMethod;
import com.adyen.commerce.connector.dto.SubscriptionCancellation;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.BillingException;
import com.adyen.commerce.connector.exception.CapabilityUnsupportedException;
import com.adyen.commerce.connector.exception.PreconditionFailedException;
import com.adyen.commerce.connector.exception.RetryableBillingException;
import com.adyen.commerce.connector.model.BillingActivationAttemptModel;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;
import com.adyen.commerce.connector.registry.SubscriptionBillingConnectorRegistry;
import com.adyen.commerce.connector.selfservice.CustomerSubscriptionService;
import com.adyen.commerce.connector.selfservice.PaymentMethodNotOwnedException;
import com.adyen.commerce.connector.selfservice.SubscriptionLifecycleState;
import com.adyen.commerce.connector.service.SubscriptionBillingService;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;
import com.adyen.commerce.connector.token.AdyenTokenHandleFactory;
import com.adyen.commerce.services.AdyenStoredCardAuthorisationService;
import com.adyen.model.checkout.StoredPaymentMethodResource;
import com.adyen.v6.exceptions.AdyenStoredCardsUnavailableException;
import com.adyen.v6.service.AdyenStoredCardsService;

import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.store.BaseStoreModel;

/**
 * Default implementation.
 *
 * <p>Ownership is enforced by predicate in the query, never by loading a row and comparing its customer
 * afterwards. Platform calls go through {@link SubscriptionBillingService}, which supplies idempotency keys,
 * validates the merchant account and records what moved; only what a connector declares about itself is
 * read from the registry directly, still in the subscription's store.</p>
 */
public class DefaultCustomerSubscriptionService implements CustomerSubscriptionService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultCustomerSubscriptionService.class);

	private FlexibleSearchService flexibleSearchService;
	private SubscriptionBillingService subscriptionBillingService;
	private SubscriptionBillingConnectorRegistry connectorRegistry;
	private SubscriptionStoreContext storeContext;
	private AdyenTokenHandleFactory tokenHandleFactory;
	private AdyenStoredCardsService storedCardsService;
	private AdyenStoredCardAuthorisationService storedCardAuthorisationService;

	@Override
	public Set<BillingPlatform> getAvailablePlatforms()
	{
		return connectorRegistry.getAvailablePlatforms();
	}

	// --- reading ---

	@Override
	public List<BillingSubscriptionRefModel> getSubscriptions(final CustomerModel customer)
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery(
				"SELECT {pk} FROM {BillingSubscriptionRef} WHERE {customer} = ?customer "
						+ "ORDER BY {currentPeriodEnd} DESC, {pk} DESC");
		query.addQueryParameter("customer", customer);
		return flexibleSearchService.<BillingSubscriptionRefModel> search(query).getResult();
	}

	@Override
	public Optional<BillingSubscriptionRefModel> findSubscription(final CustomerModel customer, final String code)
	{
		if (customer == null || StringUtils.isBlank(code))
		{
			return Optional.empty();
		}
		final FlexibleSearchQuery query = new FlexibleSearchQuery(
				"SELECT {pk} FROM {BillingSubscriptionRef} WHERE {customer} = ?customer AND {code} = ?code");
		query.addQueryParameter("customer", customer);
		query.addQueryParameter("code", code);
		final List<BillingSubscriptionRefModel> result = flexibleSearchService
				.<BillingSubscriptionRefModel> search(query).getResult();
		return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
	}

	@Override
	public List<BillingActivationAttemptModel> getAbandonedActivations(final CustomerModel customer)
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery(
				"SELECT {a.pk} FROM {BillingActivationAttempt AS a JOIN Order AS o ON {a.order} = {o.pk}} "
						+ "WHERE {o.user} = ?customer AND {a.status} = ?status ORDER BY {a.lastAttemptAt} DESC");
		query.addQueryParameter("customer", customer);
		query.addQueryParameter("status", BillingActivationAttemptService.STATUS_DEAD_LETTER);
		return flexibleSearchService.<BillingActivationAttemptModel> search(query).getResult();
	}

	/**
	 * The first question is whether any platform has ever confirmed this row, not its status.
	 * {@code platformUpdatedAt} answers it because only a reconciliation writes it; {@code lastSyncedAt}
	 * would not, since it is cleared to hurry the sweep along after a cancellation whose follow-up read
	 * failed.
	 */
	@Override
	public SubscriptionLifecycleState getState(final BillingSubscriptionRefModel subscription)
	{
		if (subscription.getPlatformUpdatedAt() == null)
		{
			return SubscriptionLifecycleState.SETTING_UP;
		}

		final boolean ending = Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd());
		final NormalizedSubscriptionStatus status = normalizedStatus(subscription.getStatus());
		if (status == null)
		{
			return SubscriptionLifecycleState.UNAVAILABLE;
		}
		return switch (status)
		{
			case ACTIVE -> ending ? SubscriptionLifecycleState.ENDING : SubscriptionLifecycleState.ACTIVE;
			// Dunning outranks the pending end, so this pair has to exist.
			case PAST_DUE -> ending ? SubscriptionLifecycleState.PAST_DUE_ENDING : SubscriptionLifecycleState.PAST_DUE;
			case PENDING -> startsInTheFuture(subscription)
					? SubscriptionLifecycleState.STARTING_SOON
					: SubscriptionLifecycleState.SETTING_UP;
			case PAUSED -> SubscriptionLifecycleState.PAUSED;
			case CANCELLED, EXPIRED, FAILED -> SubscriptionLifecycleState.ENDED;
			case UNKNOWN -> SubscriptionLifecycleState.UNAVAILABLE;
		};
	}

	protected boolean startsInTheFuture(final BillingSubscriptionRefModel subscription)
	{
		final Date start = subscription.getCurrentPeriodStart();
		return start != null && start.after(new Date());
	}

	/**
	 * The stored status as a normalized value, or {@code null} when the literal is not one. The column is a
	 * string, so it can hold a value from an older release or an unmapped platform state.
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
			LOG.warn("Subscription carries the unrecognised status literal '{}'; treating it as unavailable.", stored);
			return null;
		}
	}

	@Override
	public BaseStoreModel getStore(final BillingSubscriptionRefModel subscription)
	{
		return storeContext.storeOf(subscription);
	}

	@Override
	public String getBillingAccount(final BillingSubscriptionRefModel subscription)
	{
		final BaseStoreModel store = getStore(subscription);
		return (store == null ? "no-store" : store.getUid()) + "/" + subscription.getPlatform().getCode()
				+ "/" + subscription.getExternalCustomerId();
	}

	// --- what the connector declares ---

	/**
	 * Read in the subscription's own store: a connector may derive its capabilities from that store's
	 * configuration, and two stores selling on one platform need not agree.
	 */
	@Override
	public PaymentMethodChangeSupport getPaymentMethodChangeSupport(final BillingSubscriptionRefModel subscription)
	{
		// Nothing can act without the store, and the row already says so; not a connector problem.
		if (getStore(subscription) == null)
		{
			return PaymentMethodChangeSupport.NONE;
		}
		try
		{
			return inStoreOf(subscription, connector -> connector.capabilities().paymentMethodChange());
		}
		catch (final BillingException | RuntimeException e)
		{
			LOG.info("No connector can be asked for platform {} in the store of subscription '{}'; treating "
					+ "its payment method as unchangeable.", subscription.getPlatform(), subscription.getCode());
			return PaymentMethodChangeSupport.NONE;
		}
	}

	@Override
	public PaymentMethodEnrollmentSupport getPaymentMethodEnrollmentSupport(
			final BillingSubscriptionRefModel subscription)
	{
		try
		{
			return inStoreOf(subscription, connector -> connector.capabilities().paymentMethodEnrollment());
		}
		catch (final BillingException | RuntimeException e)
		{
			return PaymentMethodEnrollmentSupport.NONE;
		}
	}

	protected boolean requiresNetworkTransactionId(final BillingSubscriptionRefModel subscription)
	{
		try
		{
			return inStoreOf(subscription,
					connector -> Boolean.valueOf(connector.capabilities().requiresNetworkTransactionId())).booleanValue();
		}
		catch (final BillingException | RuntimeException e)
		{
			// Unknown means withhold: offering a card the platform then refuses is worse than offering none.
			return true;
		}
	}

	@Override
	public String getCurrentPaymentMethodId(final BillingSubscriptionRefModel subscription)
	{
		if (StringUtils.isBlank(subscription.getExternalPaymentMethodId()))
		{
			return null;
		}
		try
		{
			return inStoreOf(subscription,
					connector -> connector.listedPaymentMethodId(subscription.getExternalPaymentMethodId()));
		}
		catch (final BillingException | RuntimeException e)
		{
			return null;
		}
	}

	/** Work against the subscription's own connector, with its store in context. */
	protected <T> T inStoreOf(final BillingSubscriptionRefModel subscription, final ConnectorWork<T> work)
			throws BillingException
	{
		return storeContext.callInStoreOf(subscription,
				store -> work.call(connectorRegistry.getConnector(subscription.getPlatform())));
	}

	@FunctionalInterface
	protected interface ConnectorWork<T>
	{
		T call(SubscriptionBillingConnector connector) throws BillingException;
	}

	// --- payment methods ---

	@Override
	public List<PlatformPaymentMethod> getPlatformPaymentMethods(final BillingSubscriptionRefModel subscription)
			throws BillingException
	{
		return subscriptionBillingService.listPaymentMethods(subscription);
	}

	/**
	 * A platform that charges an imported token as a merchant-initiated transaction needs the network
	 * transaction id of the authorisation that vaulted it, and Adyen does not report one for every token, so
	 * such a card is left out rather than offered and then refused.
	 */
	@Override
	public List<PlatformPaymentMethod> getAcceptedVaultedCards(final CustomerModel customer,
			final BillingSubscriptionRefModel subscription)
	{
		if (!getPaymentMethodChangeSupport(subscription).accepts(PaymentMethodSource.ADYEN_VAULTED_TOKEN))
		{
			return List.of();
		}
		final boolean needsNtid = requiresNetworkTransactionId(subscription);
		final List<StoredPaymentMethodResource> vault = readVault(customer, subscription);
		if (vault.isEmpty())
		{
			return List.of();
		}
		final Map<String, String> known = knownNetworkTxReferences(customer);

		final List<PlatformPaymentMethod> cards = new ArrayList<>();
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
			cards.add(new PlatformPaymentMethod(card.getId(), vaultLabel(card), cardMetadataOf(card), false));
		}
		// Presence only, never the value: it is a scheme-level payment identifier.
		LOG.info("Adyen vault for subscription '{}': {} card(s), {} offered, {} withheld for carrying no "
				+ "network transaction id (platform requires one: {}).", subscription.getCode(),
				Integer.valueOf(vault.size()), Integer.valueOf(cards.size()),
				Integer.valueOf(withoutNetworkTransactionId), Boolean.valueOf(needsNtid));
		return cards;
	}

	/**
	 * The customer's vault as the subscription's store sees it, or nothing: a listing that cannot be read
	 * withholds cards rather than failing the caller.
	 */
	protected List<StoredPaymentMethodResource> readVault(final CustomerModel customer,
			final BillingSubscriptionRefModel subscription)
	{
		try
		{
			return storeContext.callInStoreOf(subscription, store -> vaultListing(customer));
		}
		catch (final BillingException | RuntimeException e)
		{
			LOG.warn("Could not read the shopper's Adyen vault for subscription '{}'; offering no vaulted cards.",
					subscription.getCode(), e);
			return List.of();
		}
	}

	/**
	 * The customer's vault listing for the merchant account of the store in context, never {@code null}. Read
	 * for the customer passed in rather than the one in session, so the check is about whom the caller named.
	 *
	 * @throws RetryableBillingException when Adyen could not be asked. That says nothing about ownership, so it
	 *         is not an empty listing: an empty one would turn an outage into "this is not your card".
	 */
	protected List<StoredPaymentMethodResource> vaultListing(final CustomerModel customer)
			throws RetryableBillingException
	{
		try
		{
			final List<StoredPaymentMethodResource> vault = storedCardsService.getStoredCardsOrThrow(customer);
			return vault == null ? List.of() : vault;
		}
		catch (final AdyenStoredCardsUnavailableException e)
		{
			throw new RetryableBillingException("The shopper's Adyen vault could not be read", e);
		}
	}

	/**
	 * The network transaction id for a vaulted card, from whichever source has one: the vault listing first,
	 * then what was captured when the card was authorised, since Adyen reports it once and not again.
	 */
	protected String networkTxReferenceOf(final StoredPaymentMethodResource card, final Map<String, String> known)
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

	/** What a vaulted card is called. The vault reports brand and last four separately. */
	protected String vaultLabel(final StoredPaymentMethodResource card)
	{
		final String lastFour = StringUtils.trimToNull(card.getLastFour());
		final String brand = StringUtils.trimToNull(card.getBrand());
		if (lastFour == null)
		{
			return StringUtils.defaultIfBlank(brand, "Saved card");
		}
		return brand == null ? "•••• " + lastFour
				: brand + " •••• " + lastFour;
	}

	protected CardMetadata cardMetadataOf(final StoredPaymentMethodResource card)
	{
		return card == null ? null
				: new CardMetadata(null, card.getLastFour(), card.getHolderName(),
						expiry(card.getExpiryMonth(), card.getExpiryYear()), null);
	}

	/** The handle carries the expiry as MM/YYYY. */
	protected String expiry(final String month, final String year)
	{
		return StringUtils.isAnyBlank(month, year) ? null
				: StringUtils.leftPad(month, 2, '0') + "/" + (year.length() == 2 ? "20" + year : year);
	}

	// --- changing the payment method ---

	/**
	 * Re-derives everything from the row rather than trusting the page that offered the control, which may
	 * be minutes old.
	 */
	@Override
	public PaymentMethodChangeScope changePaymentMethod(final CustomerModel customer,
			final BillingSubscriptionRefModel subscription, final String paymentMethodId) throws BillingException
	{
		if (StringUtils.isBlank(paymentMethodId))
		{
			throw new PreconditionFailedException("No payment method was chosen");
		}
		if (!getState(subscription).isPaymentMethodChangeable())
		{
			throw new PreconditionFailedException("Subscription '" + subscription.getCode()
					+ "' is not in a state where changing its payment method could achieve anything");
		}
		// Checked before the capability: asking a connector what it supports needs the row's store.
		if (getStore(subscription) == null)
		{
			throw new PreconditionFailedException("Subscription '" + subscription.getCode()
					+ "' has no originating store, so its platform cannot be reached");
		}
		final PaymentMethodChangeSupport support = getPaymentMethodChangeSupport(subscription);
		if (!support.isSupported())
		{
			throw new CapabilityUnsupportedException("The platform behind subscription '" + subscription.getCode()
					+ "' does not offer a payment-method change");
		}

		// Read in the subscription's store: the vault and the platform's own listing are both answered for
		// the store in context.
		final PaymentMethodChoice choice = storeContext.callInStoreOf(subscription,
				store -> choiceFor(customer, subscription, support, paymentMethodId, store));
		final PaymentMethodChangeScope applied =
				subscriptionBillingService.changePaymentMethod(subscription, choice).appliedScope();
		if (applied == null)
		{
			// Not defaulted: the scope decides what the shopper is told.
			throw new IllegalStateException("The payment-method change did not report the scope it applied");
		}
		return applied;
	}

	/**
	 * Reads the posted id the way the connector's declared sources allow: the same opaque id is an Adyen
	 * vault reference for one source and the platform's own id for the other. Neither reading trusts it.
	 */
	protected PaymentMethodChoice choiceFor(final CustomerModel customer,
			final BillingSubscriptionRefModel subscription, final PaymentMethodChangeSupport support,
			final String paymentMethodId, final BaseStoreModel store) throws BillingException
	{
		if (support.accepts(PaymentMethodSource.ALREADY_ON_PLATFORM))
		{
			// A listing that could not be read says nothing about ownership, so it surfaces as a failure.
			final List<PlatformPaymentMethod> offered = getPlatformPaymentMethods(subscription);
			if (offered.stream().anyMatch(method -> paymentMethodId.equals(method.id())))
			{
				return new PaymentMethodChoice.AlreadyOnPlatform(paymentMethodId);
			}
			if (!support.accepts(PaymentMethodSource.ADYEN_VAULTED_TOKEN))
			{
				throw new PaymentMethodNotOwnedException("The chosen payment method is not one this "
						+ "subscription's platform holds for this customer");
			}
		}
		return vaultedChoice(customer, paymentMethodId, store);
	}

	protected PaymentMethodChoice vaultedChoice(final CustomerModel customer, final String storedPaymentMethodId,
			final BaseStoreModel store) throws BillingException
	{
		final StoredPaymentMethodResource card = ownedCard(customer, storedPaymentMethodId);
		return new PaymentMethodChoice.AdyenVaultedToken(tokenHandleFactory.createForVaultedToken(customer, store,
				storedPaymentMethodId, networkTxReferenceOf(card, knownNetworkTxReferences(customer)),
				cardMetadataOf(card)));
	}

	/**
	 * The shopper's own vaulted card with this id. This is the access check: the id arrives as a request
	 * parameter, and a request need not come from the page that listed the cards.
	 *
	 * @throws PaymentMethodNotOwnedException when the vault was read and the card is not in it
	 * @throws RetryableBillingException when the vault could not be read, which decides nothing about ownership
	 */
	protected StoredPaymentMethodResource ownedCard(final CustomerModel customer, final String storedPaymentMethodId)
			throws BillingException
	{
		final List<StoredPaymentMethodResource> vault;
		try
		{
			vault = vaultListing(customer);
		}
		catch (final RuntimeException e)
		{
			throw new IllegalStateException("Could not read the shopper's stored cards; refusing to change a "
					+ "payment method without establishing that the chosen one is theirs", e);
		}
		return vault.stream()
				.filter(stored -> storedPaymentMethodId.equals(stored.getId()))
				.findFirst()
				.orElseThrow(() -> new PaymentMethodNotOwnedException(
						"The chosen stored payment method is not among this shopper's vaulted cards"));
	}

	@Override
	public List<BillingSubscriptionRefModel> getSubscriptionsSharingBillingAccount(final CustomerModel customer,
			final BillingSubscriptionRefModel subscription)
	{
		final BaseStoreModel store = getStore(subscription);
		final List<BillingSubscriptionRefModel> siblings = new ArrayList<>();
		for (final BillingSubscriptionRefModel other : getSubscriptions(customer))
		{
			// Identified by what the platform calls it, not by PK: that is the identity the change acts on.
			// The same account as getBillingAccount describes it, compared field by field.
			if (StringUtils.equals(other.getExternalSubscriptionId(), subscription.getExternalSubscriptionId())
					|| !StringUtils.equals(other.getPlatform().getCode(), subscription.getPlatform().getCode())
					|| !StringUtils.equals(other.getExternalCustomerId(), subscription.getExternalCustomerId())
					|| getStore(other) == null
					|| !Objects.equals(getStore(other), store)
					|| !getState(other).isPaymentMethodChangeable()
					// Its own store's configuration decides, and two stores on one platform need not agree.
					|| !getPaymentMethodChangeSupport(other).isSupported())
			{
				continue;
			}
			siblings.add(other);
		}
		return siblings;
	}

	// --- cancelling and the hosted page ---

	@Override
	public void cancelAtPeriodEnd(final BillingSubscriptionRefModel subscription) throws BillingException
	{
		if (!getState(subscription).isCancellable())
		{
			throw new PreconditionFailedException("Subscription '" + subscription.getCode()
					+ "' is not in a state that can be cancelled by the shopper");
		}
		if (getStore(subscription) == null)
		{
			throw new PreconditionFailedException("Subscription '" + subscription.getCode()
					+ "' has no originating store, so its platform cannot be reached");
		}
		subscriptionBillingService.cancel(subscription,
				SubscriptionCancellation.endOfPeriod(CancelReason.REQUESTED_BY_CUSTOMER));
	}

	@Override
	public Optional<PaymentMethodEnrollmentPage> getPaymentMethodEnrollmentPage(
			final BillingSubscriptionRefModel subscription) throws BillingException
	{
		return subscriptionBillingService.paymentMethodEnrollmentPage(subscription);
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}

	public void setSubscriptionBillingService(final SubscriptionBillingService subscriptionBillingService)
	{
		this.subscriptionBillingService = subscriptionBillingService;
	}

	public void setConnectorRegistry(final SubscriptionBillingConnectorRegistry connectorRegistry)
	{
		this.connectorRegistry = connectorRegistry;
	}

	public void setStoreContext(final SubscriptionStoreContext storeContext)
	{
		this.storeContext = storeContext;
	}

	public void setTokenHandleFactory(final AdyenTokenHandleFactory tokenHandleFactory)
	{
		this.tokenHandleFactory = tokenHandleFactory;
	}

	public void setStoredCardsService(final AdyenStoredCardsService storedCardsService)
	{
		this.storedCardsService = storedCardsService;
	}

	public void setStoredCardAuthorisationService(
			final AdyenStoredCardAuthorisationService storedCardAuthorisationService)
	{
		this.storedCardAuthorisationService = storedCardAuthorisationService;
	}
}
