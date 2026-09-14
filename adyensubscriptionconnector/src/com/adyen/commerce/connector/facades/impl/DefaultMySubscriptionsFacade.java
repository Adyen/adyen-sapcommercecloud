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
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adyen.commerce.connector.activation.BillingActivationAttemptService;
import com.adyen.commerce.connector.context.SubscriptionBaseStoreSelectorStrategy;
import com.adyen.commerce.connector.dto.CancelReason;
import com.adyen.commerce.connector.dto.PaymentMethodChangeScope;
import com.adyen.commerce.connector.dto.NormalizedSubscriptionStatus;
import com.adyen.commerce.connector.dto.SubscriptionCancellation;
import com.adyen.commerce.connector.exception.ConnectorNotConfiguredException;
import com.adyen.commerce.connector.facades.MySubscriptionsFacade;
import com.adyen.commerce.connector.facades.data.PaymentMethodChangeResult;
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
 * <h3>How ownership is enforced</h3>
 * <p>By predicate, in the query, every time — never by loading a row and comparing its customer afterwards.
 * The two are not equivalent under a mistake: a comparison that is forgotten, or short-circuited by a null,
 * silently returns somebody else's subscription, whereas a query that names the customer returns nothing.
 * The customer always comes from the session and is never accepted as an argument.</p>
 *
 * <h3>Why every operation runs inside the order's own store</h3>
 * <p>The connectors read their API key, site and gateway account from the base store in the session.
 * {@code Customer} is global across stores in SAP, so a shopper signed in to one storefront can hold
 * subscriptions bought on another; without putting the order's store in context, a cancellation would be
 * sent to the wrong billing account — where it either fails or, worse, names a subscription that exists
 * there.</p>
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

	@Override
	public SubscriptionOverviewData getSubscriptionsForCurrentCustomer()
	{
		final SubscriptionOverviewData overview = new SubscriptionOverviewData();
		final CustomerModel customer = currentCustomer();
		if (customer == null)
		{
			return overview;
		}

		final List<SubscriptionEntryData> entries = new ArrayList<>();
		for (final BillingSubscriptionRefModel ref : findSubscriptions(customer))
		{
			entries.add(toEntry(ref));
		}
		overview.setSubscriptions(entries);
		overview.setOrdersAwaitingSetup(findOrdersAwaitingSetup(customer));
		// Reads the entries just built rather than querying again: the offer has to describe the rows the
		// shopper is looking at, and a second query is a second answer that can disagree with the first.
		applyPaymentMethodChangeOffer(overview);
		return overview;
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
			// One line, no distinction: whether the code does not exist or belongs to somebody else is
			// exactly what a caller probing for codes wants to learn.
			LOG.info("No subscription matching the requested code belongs to the current customer; refusing.");
			return false;
		}

		// Re-derived rather than trusted from the form. The page that offered the button may have been
		// rendered minutes ago, and the subscription may have ended or been cancelled since.
		if (!displayState(ref).isCancellable())
		{
			LOG.info("Subscription '{}' is not in a state that can be cancelled by the shopper; refusing.", code);
			return false;
		}

		try
		{
			cancelInStoreContext(ref);
			return true;
		}
		catch (final RuntimeException e)
		{
			// The shopper is told it did not work and can try again; the detail stays in the log, because a
			// platform's own error text is not something to put in front of a customer.
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
		// Said out loud, with which half was missing. This used to return in silence, and the result was a
		// change that failed with an error message on screen and not one line anywhere explaining it - the
		// blank code being exactly what a reference predating the public identifier produces.
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
		// Re-derived rather than trusted from the form, exactly as the cancellation does. The page that
		// offered the control may be minutes old, and a row that has ended accepts nothing.
		if (!displayState(ref).isPaymentMethodChangeable())
		{
			LOG.info("Subscription '{}' is not in a state where changing the payment method could achieve "
					+ "anything; refusing.", subscriptionCode);
			return PaymentMethodChangeResult.FAILED;
		}
		// No platform name here any more. Whether this can be done at all, and whose billing it moves, is
		// the connector's declaration; the service enforces it and this facade only reports what came back.
		if (!declaredScopeFor(ref).isSupported())
		{
			LOG.info("The billing platform behind subscription '{}' does not offer a payment-method change.",
					subscriptionCode);
			return PaymentMethodChangeResult.NOT_SUPPORTED_HERE;
		}

		try
		{
			final PaymentMethodChangeScope applied = changeInStoreContext(customer, ref, storedPaymentMethodId);
			return applied == PaymentMethodChangeScope.CUSTOMER
					? PaymentMethodChangeResult.CHANGED_ALL_SUBSCRIPTIONS
					: PaymentMethodChangeResult.CHANGED_THIS_SUBSCRIPTION;
		}
		catch (final TokenNotOwnedException e)
		{
			// Not an error: a request naming a token this shopper does not have is refused, and refused the
			// same way as a subscription that is not theirs. The vault listing is the authority here.
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

	/**
	 * What the connector behind this row says a payment-method change would move.
	 *
	 * <p>Resolved from the row's own platform, never from the store's active one: after a store migrates
	 * from one platform to another its old subscriptions still live on the old one, and asking the new
	 * connector about them would describe an operation on the wrong system. A row whose adapter is not
	 * deployed answers {@code NOT_SUPPORTED} rather than taking the page down with it.</p>
	 */
	protected PaymentMethodChangeScope declaredScopeFor(final BillingSubscriptionRefModel ref)
	{
		try
		{
			return connectorRegistry.getConnector(ref.getPlatform()).capabilities().paymentMethodChange();
		}
		catch (final ConnectorNotConfiguredException | RuntimeException e)
		{
			LOG.info("No connector is deployed for platform {}; treating its subscriptions as unchangeable.",
					ref.getPlatform());
			return PaymentMethodChangeScope.NOT_SUPPORTED;
		}
	}

	/**
	 * Raised when the chosen token is not among the cards Adyen holds for this shopper.
	 *
	 * <p>Its own type because the caller has to tell it apart from a failure: "this is not your card" is a
	 * decision and must never be retried, whereas a vault listing that could not be read is transient and
	 * says nothing about ownership. Both used to end here as {@code null} card metadata and a request that
	 * carried on regardless.</p>
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
	 * Performs the change with the subscription's own store in context, and answers what it moved.
	 *
	 * <p>Through {@code SubscriptionBillingService} rather than the connector registry directly. The
	 * shortcut cost three things every time it was taken: no idempotency key, so a replayed request was a
	 * second real call; no merchant-account validation, so a mismatched gateway was found by the platform
	 * rather than by us; and no local record of the instrument that is now billing.</p>
	 *
	 * <p>The card metadata is whatever the vault listing gave us and may be incomplete - the platform shows
	 * it, nothing depends on it.</p>
	 */
	protected PaymentMethodChangeScope changeInStoreContext(final CustomerModel customer,
			final BillingSubscriptionRefModel ref, final String storedPaymentMethodId)
	{
		final BaseStoreModel store = storeOf(ref);
		if (store == null)
		{
			throw new IllegalStateException("Subscription has no originating store; its connector's "
					+ "credentials cannot be selected safely");
		}

		final Map<String, Object> sessionParameters = Collections.singletonMap(
				SubscriptionBaseStoreSelectorStrategy.CURRENT_SUBSCRIPTION_BASE_STORE, store);
		final Object applied = sessionService.executeInLocalViewWithParams(sessionParameters,
				new SessionExecutionBody()
				{
					@Override
					public Object execute()
					{
						// Inside the local view on purpose. The vault is read against the store in context,
						// and the store that matters is the subscription's, not the session's: a shopper
						// with subscriptions bought in two storefronts would otherwise be checked against
						// the wrong merchant account.
						final StoredPaymentMethodResource card = ownedCard(customer, storedPaymentMethodId);
						try
						{
							final AdyenTokenHandle token = tokenHandleFactory.createForStoredToken(customer,
									store, storedPaymentMethodId, cardMetadataOf(card));
							return subscriptionBillingService.changePaymentMethod(ref, token).appliedScope();
						}
						catch (final Exception e)
						{
							throw new IllegalStateException(e);
						}
					}
				});

		if (!(applied instanceof PaymentMethodChangeScope))
		{
			// Unreachable unless the local-view contract changes under us, and deliberately not defaulted:
			// the scope decides which sentence the shopper reads, and inventing one would be the single
			// place in this design where a wiring fault turns into a false statement to a person.
			throw new IllegalStateException("The payment-method change did not report the scope it applied; "
					+ "refusing to describe it rather than guess");
		}
		return (PaymentMethodChangeScope) applied;
	}

	/**
	 * The shopper's own card with this id, or a refusal.
	 *
	 * <p>This is the access check, and until it existed there was none: the token id arrives as a request
	 * parameter and nothing on the way to the platform compared it against the cards this shopper actually
	 * has. The listing rendered into the page is not a control — a request does not have to come from that
	 * page. What stood in for a check was Adyen refusing to retrieve a token that does not belong to the
	 * shopper reference taken from the session, which is a property of one platform's import call
	 * ({@code liveTokenValidationOnImport}) and not something every connector promises.</p>
	 *
	 * <p>A listing that could not be read is <em>not</em> an answer about ownership, so it is raised as a
	 * failure rather than a refusal. Conflating the two is what let the previous version continue: it caught
	 * everything, returned no metadata, and carried on to the platform.</p>
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
	 * <p>Built from the object {@link #ownedCard} already returned rather than looked up again: the lookup
	 * is the access check, and doing it twice invites the two answers to differ. Only the platform's own
	 * screens show this, so an absent field costs nothing there.</p>
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
	 * <p>Two separate answers, because they are separate questions.</p>
	 *
	 * <p><b>The control above the list</b> is offered only for a {@code CUSTOMER}-scoped row. That scope
	 * means the change moves every subscription the customer has on that platform, so one control for the
	 * whole page is the honest shape and a control per row would be a lie told several times over — press
	 * it on the third row and the first two move with it. A {@code SUBSCRIPTION}-scoped row gets its
	 * control in the row instead, where what it affects is what it sits next to.</p>
	 *
	 * <p><b>Whether anything at all can be changed</b> is tracked separately, because without it the page
	 * cannot tell "there is no control above the list" apart from "this cannot be done here" — and a
	 * shopper whose subscriptions are all pinned per subscription would be told the change is unavailable
	 * while a working control sat in every row.</p>
	 *
	 * <p>Every way of offering nothing is written down. The first version of this guard turned a form that
	 * failed on submission into a form that was simply absent, absent for a reason nothing in the log
	 * explained — the same defect in a quieter costume. The missing-identifier case is a data gap with a
	 * known remedy, so it names it.</p>
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
				// Asked of the platform, not of today's data: a row that is merely too new to act on says
				// nothing about whether its provider can change cards.
				overview.setPaymentMethodChangeSupportedSomewhere(true);
			}

			if (entry.isPaymentMethodChangeable())
			{
				overview.setAnyPaymentMethodChangeable(true);
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
	 * it. Reading a row's own "can I be named" flag as "will I be changed" is what produced a page telling
	 * a shopper one subscription's card could not be changed online, directly beneath a control that was
	 * about to change it.</p>
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
	 * Writes down every way of offering nothing.
	 *
	 * <p>The first version of this guard turned a form that failed on submission into a form that was
	 * simply absent, absent for a reason nothing in the log explained — the same defect in a quieter
	 * costume. The missing-identifier case is a data gap with a known remedy, so it names it.</p>
	 *
	 * <p>The missing-code warning is emitted even when the page did offer something, which an earlier
	 * version suppressed. That is precisely the case an operator needs told: rows that could be offered are
	 * being left out while others work, so nothing on screen looks wrong and nobody goes looking.</p>
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
			// Not a problem, and said at INFO for that reason: this is the feature working. The counts are
			// there so "why is there no control" has an answer without a debugger.
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
	 * <p>Only the dead letter, not every failed attempt: an attempt that is still being retried is expected
	 * to succeed shortly and telling the shopper about it would be alarming and premature.</p>
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
		final SubscriptionEntryData entry = new SubscriptionEntryData();
		entry.setCode(ref.getCode());
		entry.setProductName(displayName(ref));
		final SubscriptionDisplayState state = displayState(ref);
		entry.setState(state);
		entry.setEffectiveDate(effectiveDate(ref, state));
		entry.setQuantity(ref.getQuantity());

		// Asked of this row's own connector, not of the store's active one: after a store migrates from one
		// platform to another its older subscriptions still live on the old one, and describing them with
		// the new platform's abilities would offer an operation on the wrong system.
		final PaymentMethodChangeScope scope = declaredScopeFor(ref);
		entry.setPaymentMethodChangeScope(scope);
		entry.setPaymentMethodChangeable(scope.isSupported() && state.isPaymentMethodChangeable()
				&& StringUtils.isNotBlank(ref.getCode()));

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
	 * a catch. A subscription outlives the catalogue entry it was sold from, and it must still be listed and
	 * still be cancellable when that happens.</p>
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
	 * {@code platformUpdatedAt} answers it, because only a reconciliation writes it — {@code lastSyncedAt}
	 * would not, since it is deliberately cleared to hurry the sweep along after a cancellation whose
	 * follow-up read failed, and reading that as "never confirmed" is how a subscription the shopper had
	 * just stopped came to be labelled as still being set up.</p>
	 */
	protected SubscriptionDisplayState displayState(final BillingSubscriptionRefModel ref)
	{
		if (storeOf(ref) == null)
		{
			// Nothing can be said, and more importantly nothing can be done: without the store there are no
			// credentials with which to reach the platform this belongs to.
			return SubscriptionDisplayState.UNAVAILABLE;
		}
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
			// Dunning outranks the pending end on both adapters, so this pair has to exist: without it a
			// shopper who had already stopped their subscription would be told their provider will retry.
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
	 * <p>The column is a string, so it can hold a value this vocabulary no longer has — from an older
	 * release, or from a platform that grew a state we have not mapped. Guessing at it would put a wrong
	 * word in front of a shopper; {@code null} sends the row to {@code UNAVAILABLE}, which says nothing and
	 * offers nothing.</p>
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
					// Rewrapped because SessionExecutionBody cannot throw a checked exception; the caller's
					// catch is what turns this into "no" on screen rather than an error page.
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
	 * <p>A guest is deliberately included in "anyone else" by virtue of never reaching this page: the
	 * accelerator confines {@code /my-account/**} to {@code ROLE_CUSTOMERGROUP}. Activation now refuses to
	 * start a subscription for a guest at all, precisely because this page is the only way to stop one.</p>
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

	public void setStoredCardsFacade(final AdyenStoredCardsFacade storedCardsFacade)
	{
		this.storedCardsFacade = storedCardsFacade;
	}
}
