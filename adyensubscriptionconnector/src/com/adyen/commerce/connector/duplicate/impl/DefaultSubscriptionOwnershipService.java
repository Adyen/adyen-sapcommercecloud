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
package com.adyen.commerce.connector.duplicate.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adyen.commerce.connector.activation.BillingActivationAttemptService;
import com.adyen.commerce.connector.dto.NormalizedSubscription;
import com.adyen.commerce.connector.dto.PlanRef;
import com.adyen.commerce.connector.dto.PlanResolutionRequest;
import com.adyen.commerce.connector.duplicate.BlockingSubscription;
import com.adyen.commerce.connector.duplicate.BlockingSubscription.Source;
import com.adyen.commerce.connector.duplicate.DuplicateCheckRequest;
import com.adyen.commerce.connector.duplicate.DuplicateCheckStage;
import com.adyen.commerce.connector.duplicate.DuplicateSubscriptionRuleStrategy;
import com.adyen.commerce.connector.duplicate.Freshness;
import com.adyen.commerce.connector.duplicate.PlanLookup;
import com.adyen.commerce.connector.duplicate.SubscriptionOwnershipService;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.enums.DuplicateSubscriptionRule;
import com.adyen.commerce.connector.exception.BillingException;
import com.adyen.commerce.connector.exception.ConnectorNotConfiguredException;
import com.adyen.commerce.connector.exception.PlanNotMappedException;
import com.adyen.commerce.connector.exception.SubscriptionProductUndecidableException;
import com.adyen.commerce.connector.log.ConnectorLogEvent;
import com.adyen.commerce.connector.model.BillingActivationAttemptModel;
import com.adyen.commerce.connector.model.BillingCustomerRefModel;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;
import com.adyen.commerce.connector.product.SubscriptionProductRule;
import com.adyen.commerce.connector.reconciliation.SubscriptionReconciliationService;
import com.adyen.commerce.connector.registry.SubscriptionBillingConnectorRegistry;
import com.adyen.commerce.connector.selfservice.CustomerSubscriptionService;
import com.adyen.commerce.connector.selfservice.SubscriptionLifecycleState;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;

import de.hybris.platform.core.enums.OrderStatus;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.payment.dto.TransactionStatus;
import de.hybris.platform.payment.enums.PaymentTransactionType;
import de.hybris.platform.payment.model.PaymentTransactionEntryModel;
import de.hybris.platform.payment.model.PaymentTransactionModel;
import de.hybris.platform.servicelayer.user.UserService;
import de.hybris.platform.store.BaseStoreModel;

/**
 * Default ownership decision.
 *
 * <p>Order of evidence, cheapest first, stopping at the first block: the customer's own subscription records,
 * recent orders whose subscription has not been set up yet, and, only for {@link Freshness#REFRESH}, what the
 * billing platform lists for the customer. Everything the platform says is used in memory for this one decision;
 * a listed subscription this system has no record of is never saved, because it would have no order.</p>
 *
 * <p>A failure to reach the platform is never a reason to refuse a purchase: the decision is made on local data
 * and the degradation is logged.</p>
 *
 * <p>The connector is asked in the session's current base store, as everywhere a storefront request calls one;
 * callers that run without one (cron, notification threads) are not a source of purchases.</p>
 *
 * <p><b>Scope.</b> The customer's subscription records ({@code BillingSubscriptionRef}) are customer-wide, in
 * every store: a subscription is real whichever storefront sold it, and the billing platform lists by customer
 * anyway. Orders still being set up are looked at in the checked store only, because an order exists in one
 * store and the rule is configured per store.</p>
 *
 * <p><b>Deadline.</b> {@link Freshness#REFRESH} spends at most {@code refreshBudgetMillis} on the billing
 * platform: the time is checked before each stale-record refresh and each listing page, and once it has run out
 * the decision is made on what is known (fail-open, logged as {@code budget_exhausted}). The first failed
 * refresh ends the refreshes, since the next would most likely wait for the same timeout. This is a deadline
 * between calls, not a way to interrupt one: no thread is started (the hybris session and tenant would not follow
 * it), so a single call that hangs is bounded only by the connector's transport timeout.</p>
 */
public class DefaultSubscriptionOwnershipService implements SubscriptionOwnershipService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultSubscriptionOwnershipService.class);

	public static final String EVENT_BLOCKED = "subscription_duplicate_blocked";
	public static final String EVENT_DEGRADED = "subscription_duplicate_check_degraded";
	static final String REASON_BUDGET_EXHAUSTED = "budget_exhausted";

	/**
	 * States in which the subscription still serves, or is about to serve, the customer. {@code ENDED} and
	 * {@code UNAVAILABLE} are left out: the first is over, and the second is a status nobody can interpret, which
	 * is not grounds to refuse a purchase. A trial is not a state of its own: both platforms report it as an
	 * active subscription, or as a future one while it has not started, and both block.
	 */
	private static final Set<SubscriptionLifecycleState> BLOCKING_STATES = EnumSet.of(
			SubscriptionLifecycleState.SETTING_UP, SubscriptionLifecycleState.ACTIVE,
			SubscriptionLifecycleState.STARTING_SOON, SubscriptionLifecycleState.ENDING,
			SubscriptionLifecycleState.PAUSED, SubscriptionLifecycleState.PAST_DUE,
			SubscriptionLifecycleState.PAST_DUE_ENDING);

	/**
	 * Order states in which the order will never become a subscription and holds no money: cancelled (including a
	 * 3DS/redirect order that failed), cancelling, not authorised, a payment that was restored to the cart, charged back, and rejected (a B2B approval refusal; the order never reached
	 * payment).
	 *
	 * <p>Left out on purpose, so they keep blocking: {@code PAYMENT_AMOUNT_NOT_RESERVED} (set after the
	 * authorisation was confirmed, so the amount is held at Adyen) and {@code PAYMENT_NOT_CAPTURED} (also the
	 * interim state while the process waits for the capture, and a failed capture leaves the authorisation held).
	 * {@code CHECKED_INVALID}, {@code PROCESSING_ERROR} and {@code PAYMENT_PENDING}
	 * depend on the payment, see {@link #isFailedOrAbandoned}.</p>
	 */
	private static final Set<OrderStatus> DEAD_ORDER_STATES = Set.of(OrderStatus.CANCELLED, OrderStatus.CANCELLING,
			OrderStatus.PAYMENT_NOT_AUTHORIZED, OrderStatus.CHARGEBACK, OrderStatus.REJECTED);

	/**
	 * Adyen result codes (kept in the order's {@code statusInfo} when a pending order is placed) that mean the
	 * shopper still has something to do: the payment has not been made, and may never be. An order left on one of
	 * these is a 3DS or redirect that was abandoned, unless an authorisation has been recorded since. Lower case.
	 * {@code PresentToShopper} (a voucher or bank-transfer slip to pay later) is deliberately not here: the shopper
	 * has done what was asked of them and the payment can still arrive, so that order keeps blocking.
	 */
	private static final Set<String> SHOPPER_ACTION_RESULT_CODES = Set.of("redirectshopper", "challengeshopper",
			"identifyshopper");

	private FlexibleSearchService flexibleSearchService;
	private UserService userService;
	private SubscriptionBillingConnectorRegistry connectorRegistry;
	private SubscriptionProductRule subscriptionProductRule;
	private CustomerSubscriptionService customerSubscriptionService;
	private SubscriptionReconciliationService reconciliationService;
	private Map<DuplicateSubscriptionRule, DuplicateSubscriptionRuleStrategy> strategies = Map.of();
	private int staleAfterMinutes = 15;
	private int pendingOrderWindowHours = 48;
	private long refreshBudgetMillis = 5000;
	private Clock clock = Clock.systemUTC();

	@Override
	public Optional<BlockingSubscription> findBlocking(final DuplicateCheckRequest request)
	{
		if (request == null || request.customer() == null || request.product() == null
				|| request.baseStore() == null || StringUtils.isBlank(request.product().getCode())
				|| userService.isAnonymousUser(request.customer()))
		{
			return Optional.empty();
		}
		final DuplicateSubscriptionRule rule = ruleOf(request.baseStore());
		if (rule == DuplicateSubscriptionRule.DISABLED)
		{
			return Optional.empty();
		}

		final SubscriptionBillingConnector connector;
		try
		{
			connector = connectorRegistry.getActiveConnector(request.baseStore());
		}
		catch (final ConnectorNotConfiguredException e)
		{
			// A store without a billing platform sells no subscriptions.
			return Optional.empty();
		}
		try
		{
			if (!subscriptionProductRule.isSubscriptionProduct(connector, request.baseStore(), request.product()))
			{
				return Optional.empty();
			}
		}
		catch (final SubscriptionProductUndecidableException e)
		{
			degraded(request, rule, connector.platform(), "product_undecidable", e);
			return Optional.empty();
		}
		final DuplicateSubscriptionRuleStrategy strategy = strategies.get(rule);
		if (strategy == null)
		{
			degraded(request, rule, connector.platform(), "no_strategy_for_rule", null);
			return Optional.empty();
		}

		final List<BillingSubscriptionRefModel> subscriptions = customerSubscriptionService
				.getSubscriptions(request.customer());
		final Check check = new Check(request, rule, connector, strategy,
				newPlanLookup(connector, request.baseStore().getUid()),
				subscriptions == null ? List.of() : subscriptions,
				new RefreshBudget(clock.instant().plusMillis(refreshBudgetMillis)));
		final Optional<BlockingSubscription> blocking = decide(check);
		blocking.ifPresent(found -> logBlocked(check, found));
		return blocking;
	}

	protected Optional<BlockingSubscription> decide(final Check check)
	{
		final Optional<BlockingSubscription> existing = findBlockingSubscription(check);
		if (existing.isPresent())
		{
			return existing;
		}
		final Optional<BlockingSubscription> pending = findBlockingPendingOrder(check);
		if (pending.isPresent())
		{
			return pending;
		}
		return check.request().freshness() == Freshness.REFRESH ? findBlockingOnPlatform(check) : Optional.empty();
	}

	// --- the customer's own subscription records ---

	protected Optional<BlockingSubscription> findBlockingSubscription(final Check check)
	{
		final List<BillingSubscriptionRefModel> candidates = new ArrayList<>();
		for (final BillingSubscriptionRefModel ref : check.subscriptions())
		{
			if (!isOfExcludedOrder(ref.getOrder(), check))
			{
				candidates.add(ref);
			}
		}

		if (check.request().freshness() == Freshness.REFRESH)
		{
			refreshStale(check, candidates);
		}

		// Matched and judged after the refreshes: a re-read can change the plan as well as the status.
		for (final BillingSubscriptionRefModel ref : candidates)
		{
			if (matches(check, ref) && blocks(ref))
			{
				return Optional.of(new BlockingSubscription(Source.EXISTING_SUBSCRIPTION, ref.getCode(),
						customerSubscriptionService.getState(ref), check.rule()));
			}
		}
		return Optional.empty();
	}

	/**
	 * A subscription that looks like it still serves is worth a second look before it turns a customer away: it
	 * may have been cancelled on the platform since the webhook last arrived. Stops refreshing at the first failed
	 * refresh and when the budget is spent. That only ends the per-record refreshes: the listing of the customer's
	 * subscriptions on the platform ({@link #findBlockingOnPlatform}) still runs after a failed refresh for as
	 * long as budget remains, because it can only add blocks, never lift one.
	 */
	protected void refreshStale(final Check check, final List<BillingSubscriptionRefModel> candidates)
	{
		for (final BillingSubscriptionRefModel ref : candidates)
		{
			if (!matches(check, ref) || !blocks(ref) || !isStale(ref))
			{
				continue;
			}
			if (check.budget().isRefreshFailed())
			{
				return;
			}
			if (!budgetLeft(check))
			{
				budgetExhausted(check);
				return;
			}
			refresh(check, ref);
		}
	}

	protected boolean matches(final Check check, final BillingSubscriptionRefModel ref)
	{
		// A plan code means something only on its own platform; on another one it is noise.
		final String planCode = check.connector().platform().equals(ref.getPlatform()) ? ref.getPlanCode() : null;
		return check.strategy().isSameSubscription(check.plans(), check.request().product().getCode(),
				ref.getProductCode(), planCode);
	}

	/**
	 * A missing {@code lastSyncedAt} counts as stale: it is cleared to hurry the sweep along after a post-cancel
	 * read failed, so it says the record needs reading, not that it does not matter.
	 */
	protected boolean isStale(final BillingSubscriptionRefModel ref)
	{
		final Date syncedAt = ref.getLastSyncedAt();
		return syncedAt == null
				|| syncedAt.toInstant().isBefore(clock.instant().minus(Duration.ofMinutes(staleAfterMinutes)));
	}

	protected void refresh(final Check check, final BillingSubscriptionRefModel ref)
	{
		try
		{
			reconciliationService.reconcile(ref);
		}
		catch (final BillingException | RuntimeException e)
		{
			check.budget().markRefreshFailed();
			degraded(check.request(), check.rule(), check.connector().platform(), "refresh_failed", e);
		}
	}

	protected boolean budgetLeft(final Check check)
	{
		return clock.instant().isBefore(check.budget().deadline());
	}

	/** Reported once per check, however many calls it would have made. */
	protected void budgetExhausted(final Check check)
	{
		if (!check.budget().isExhaustedReported())
		{
			check.budget().markExhaustedReported();
			degraded(check.request(), check.rule(), check.connector().platform(), REASON_BUDGET_EXHAUSTED, null);
		}
	}

	/**
	 * Whether the record still serves the customer. A subscription set to end whose term has already run out
	 * does not, even though the platform has not said so yet.
	 */
	protected boolean blocks(final BillingSubscriptionRefModel ref)
	{
		final SubscriptionLifecycleState state = customerSubscriptionService.getState(ref);
		return blocks(state, ref.getCurrentPeriodEnd());
	}

	protected boolean blocks(final SubscriptionLifecycleState state, final Date currentPeriodEnd)
	{
		if (!BLOCKING_STATES.contains(state))
		{
			return false;
		}
		final boolean ending = state == SubscriptionLifecycleState.ENDING
				|| state == SubscriptionLifecycleState.PAST_DUE_ENDING;
		return !ending || currentPeriodEnd == null || currentPeriodEnd.toInstant().isAfter(clock.instant());
	}

	// --- orders whose subscription does not exist yet ---

	/**
	 * A subscription is only recorded once Adyen's notification has been processed, so a customer who paid a
	 * moment ago has none yet. Counts the orders that are still on their way to becoming one: recent, in the
	 * same store, not failed, containing the product, with no subscription and no dead-lettered activation.
	 */
	protected Optional<BlockingSubscription> findBlockingPendingOrder(final Check check)
	{
		final Date since = Date.from(clock.instant().minus(Duration.ofHours(pendingOrderWindowHours)));
		for (final OrderModel order : findRecentOrders(check.request().customer(), check.request().baseStore(), since))
		{
			if (isFailedOrAbandoned(order) || isOfExcludedOrder(order, check) || !containsSameSubscription(check, order))
			{
				continue;
			}
			if (!hasSubscriptionRef(order) && !hasDeadLetteredActivation(order))
			{
				return Optional.of(new BlockingSubscription(Source.PENDING_ORDER, null,
						SubscriptionLifecycleState.SETTING_UP, check.rule()));
			}
		}
		return Optional.empty();
	}

	/**
	 * Whether the order will not become a subscription and holds no money, so it must not stand in the way of a new
	 * purchase. Beyond the plainly dead states ({@link #DEAD_ORDER_STATES}), two depend on the payment:
	 * <ul>
	 * <li>{@code PAYMENT_PENDING} whose Adyen result code is one the shopper still had to act on (redirect,
	 * challenge, identify) and with no accepted authorisation: a 3DS or redirect the shopper walked away
	 * from. Placing the pending order turns the cart into an order before the shopper acts, and cancelling only
	 * copies its entries into a new cart, so without this the restored cart would be blocked by its own abandoned
	 * order. A result the shopper has finished ({@code Pending}, {@code Received}) keeps blocking, and so does any
	 * order with an accepted authorisation.</li>
	 * <li>{@code CHECKED_INVALID} and {@code PROCESSING_ERROR}: the order's process ended in error; no money is
	 * held unless an authorisation was already accepted ({@code PROCESSING_ERROR} is also set on an order whose
	 * authorisation is held, for instance when a CAPTURE notification threw).</li>
	 * </ul>
	 */
	protected boolean isFailedOrAbandoned(final OrderModel order)
	{
		final OrderStatus status = order.getStatus();
		if (status == null)
		{
			return false;
		}
		if (DEAD_ORDER_STATES.contains(status))
		{
			return true;
		}
		if (status == OrderStatus.CHECKED_INVALID || status == OrderStatus.PROCESSING_ERROR)
		{
			return !hasAcceptedAuthorization(order);
		}
		return status == OrderStatus.PAYMENT_PENDING && isAwaitingShopperAction(order)
				&& !hasAcceptedAuthorization(order);
	}

	protected boolean isAwaitingShopperAction(final OrderModel order)
	{
		final String resultCode = StringUtils.lowerCase(StringUtils.trimToNull(order.getStatusInfo()));
		return resultCode != null && SHOPPER_ACTION_RESULT_CODES.contains(resultCode);
	}

	protected boolean hasAcceptedAuthorization(final OrderModel order)
	{
		if (order.getPaymentTransactions() == null)
		{
			return false;
		}
		for (final PaymentTransactionModel transaction : order.getPaymentTransactions())
		{
			if (transaction == null || transaction.getEntries() == null)
			{
				continue;
			}
			for (final PaymentTransactionEntryModel entry : transaction.getEntries())
			{
				if (entry != null && PaymentTransactionType.AUTHORIZATION.equals(entry.getType())
						&& TransactionStatus.ACCEPTED.name().equals(entry.getTransactionStatus()))
				{
					return true;
				}
			}
		}
		return false;
	}

	protected boolean containsSameSubscription(final Check check, final AbstractOrderModel order)
	{
		for (final AbstractOrderEntryModel entry : order.getEntries())
		{
			final ProductModel product = entry == null ? null : entry.getProduct();
			if (product != null && StringUtils.isNotBlank(product.getCode())
					&& check.strategy().isSameSubscription(check.plans(), check.request().product().getCode(),
							product.getCode(), null))
			{
				return true;
			}
		}
		return false;
	}

	protected List<OrderModel> findRecentOrders(final CustomerModel customer, final BaseStoreModel store,
			final Date since)
	{
		// {versionID} IS NULL: the order itself, not the snapshots kept of it.
		final FlexibleSearchQuery query = new FlexibleSearchQuery("SELECT {pk} FROM {Order} WHERE {user} = ?customer "
				+ "AND {store} = ?store AND {creationtime} >= ?since AND {versionID} IS NULL");
		query.addQueryParameter("customer", customer);
		query.addQueryParameter("store", store);
		query.addQueryParameter("since", since);
		return flexibleSearchService.<OrderModel> search(query).getResult();
	}

	protected boolean hasSubscriptionRef(final OrderModel order)
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery(
				"SELECT {pk} FROM {BillingSubscriptionRef} WHERE {order} = ?order");
		query.addQueryParameter("order", order);
		return !flexibleSearchService.<BillingSubscriptionRefModel> search(query).getResult().isEmpty();
	}

	/** A dead letter is an activation that gave up: the shopper paid and got nothing, so it must not also block. */
	protected boolean hasDeadLetteredActivation(final OrderModel order)
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery(
				"SELECT {pk} FROM {BillingActivationAttempt} WHERE {order} = ?order AND {status} = ?status");
		query.addQueryParameter("order", order);
		query.addQueryParameter("status", BillingActivationAttemptService.STATUS_DEAD_LETTER);
		return !flexibleSearchService.<BillingActivationAttemptModel> search(query).getResult().isEmpty();
	}

	// --- what the platform holds ---

	protected Optional<BlockingSubscription> findBlockingOnPlatform(final Check check)
	{
		final String externalCustomerId = externalCustomerIdOf(check.request().customer(), check.connector().platform());
		if (externalCustomerId == null)
		{
			return Optional.empty();
		}

		if (!budgetLeft(check))
		{
			budgetExhausted(check);
			return Optional.empty();
		}
		final List<NormalizedSubscription> listed;
		try
		{
			listed = check.connector().listCustomerSubscriptions(externalCustomerId, () -> budgetLeft(check));
		}
		catch (final BillingException | RuntimeException e)
		{
			degraded(check.request(), check.rule(), check.connector().platform(), "list_failed", e);
			return Optional.empty();
		}
		if (!budgetLeft(check))
		{
			// The listing may have been cut short: what it did return is still used below.
			budgetExhausted(check);
		}

		final Map<String, BillingSubscriptionRefModel> known = new HashMap<>();
		for (final BillingSubscriptionRefModel ref : check.subscriptions())
		{
			known.put(ref.getExternalSubscriptionId(), ref);
		}
		for (final NormalizedSubscription snapshot : listed)
		{
			final BillingSubscriptionRefModel local = known.get(snapshot.subscription().externalId());
			if (local != null && isOfExcludedOrder(local.getOrder(), check))
			{
				continue;
			}
			final SubscriptionLifecycleState state = stateOf(snapshot);
			// A snapshot of a subscription we hold is compared as that record is (product first, plan only when
			// it has no product), or the same product on one plan would be blocked here and allowed at the cart.
			final String knownProduct = local == null ? null : StringUtils.trimToNull(local.getProductCode());
			if (!blocks(state, toDate(snapshot.currentPeriodEnd()))
					|| !check.strategy().isSameSubscription(check.plans(), check.request().product().getCode(),
							knownProduct, snapshot.planId()))
			{
				continue;
			}
			return Optional.of(local == null
					? new BlockingSubscription(Source.PLATFORM, null, state, check.rule())
					: new BlockingSubscription(Source.EXISTING_SUBSCRIPTION, local.getCode(), state, check.rule()));
		}
		return Optional.empty();
	}

	/**
	 * The state the snapshot would have as a local record, worked out by the one place that knows how. The
	 * record is built in memory and never saved.
	 */
	protected SubscriptionLifecycleState stateOf(final NormalizedSubscription snapshot)
	{
		final BillingSubscriptionRefModel transientRef = newTransientRef();
		transientRef.setStatus(snapshot.status().name());
		transientRef.setCancelAtPeriodEnd(snapshot.cancelAtPeriodEnd());
		transientRef.setCurrentPeriodStart(toDate(snapshot.currentPeriodStart()));
		transientRef.setCurrentPeriodEnd(toDate(snapshot.currentPeriodEnd()));
		// The platform has just confirmed it, whatever it did or did not stamp.
		transientRef.setPlatformUpdatedAt(
				toDate(snapshot.platformUpdatedAt() != null ? snapshot.platformUpdatedAt() : clock.instant()));
		return customerSubscriptionService.getState(transientRef);
	}

	/** A record that exists only to be asked its state; never saved, and never given a PK. */
	protected BillingSubscriptionRefModel newTransientRef()
	{
		return new BillingSubscriptionRefModel();
	}

	protected String externalCustomerIdOf(final CustomerModel customer, final BillingPlatform platform)
	{
		if (customer.getBillingCustomerRefs() == null)
		{
			return null;
		}
		for (final BillingCustomerRefModel ref : customer.getBillingCustomerRefs())
		{
			if (platform.equals(ref.getPlatform()) && StringUtils.isNotBlank(ref.getExternalId()))
			{
				return ref.getExternalId();
			}
		}
		return null;
	}

	// --- helpers ---

	protected DuplicateSubscriptionRule ruleOf(final BaseStoreModel store)
	{
		final DuplicateSubscriptionRule rule = store.getDuplicateSubscriptionRule();
		return rule == null ? DuplicateSubscriptionRule.SAME_PRODUCT : rule;
	}

	/** The cart or pending order the purchase belongs to is never evidence against itself. */
	protected boolean isOfExcludedOrder(final AbstractOrderModel order, final Check check)
	{
		final AbstractOrderModel excluded = check.request().excludedOrder();
		return order != null && excluded != null && (order == excluded || order.equals(excluded));
	}

	protected Date toDate(final Instant value)
	{
		return value == null ? null : Date.from(value);
	}

	protected void logBlocked(final Check check, final BlockingSubscription blocking)
	{
		ConnectorLogEvent.of(EVENT_BLOCKED)
				.platform(check.connector().platform())
				.field("stage", stageOf(check.request()))
				.field("rule", check.rule())
				.field("store", check.request().baseStore().getUid())
				.field("subscription_state", blocking.state())
				.field("source", blocking.source())
				.info(LOG);
	}

	protected void degraded(final DuplicateCheckRequest request, final DuplicateSubscriptionRule rule,
			final BillingPlatform platform, final String reason, final Exception cause)
	{
		ConnectorLogEvent.of(EVENT_DEGRADED)
				.platform(platform)
				.field("stage", stageOf(request))
				.field("rule", rule)
				.field("store", request.baseStore() == null ? null : request.baseStore().getUid())
				.reason(reason)
				.field("exception_class", cause == null ? null : cause.getClass().getName())
				.warn(LOG);
	}

	protected String stageOf(final DuplicateCheckRequest request)
	{
		final DuplicateCheckStage stage = request.stage();
		return stage == null ? "unknown" : stage.logValue();
	}

	/** What one {@code findBlocking} call works with, so the helpers do not take eight parameters each. */
	protected record Check(DuplicateCheckRequest request, DuplicateSubscriptionRule rule,
			SubscriptionBillingConnector connector, DuplicateSubscriptionRuleStrategy strategy, PlanLookup plans,
			List<BillingSubscriptionRefModel> subscriptions, RefreshBudget budget)
	{
	}

	/** The time one {@link Freshness#REFRESH} check may spend on the billing platform, and what it has used. */
	protected final class RefreshBudget
	{
		private final Instant deadline;
		private boolean exhaustedReported;
		private boolean refreshFailed;

		/** Public on a protected class: a constructor that is merely protected could not be called by a subclass of the service. */
		public RefreshBudget(final Instant deadline)
		{
			this.deadline = deadline;
		}

		protected Instant deadline()
		{
			return deadline;
		}

		protected boolean isExhaustedReported()
		{
			return exhaustedReported;
		}

		protected void markExhaustedReported()
		{
			exhaustedReported = true;
		}

		protected boolean isRefreshFailed()
		{
			return refreshFailed;
		}

		protected void markRefreshFailed()
		{
			refreshFailed = true;
		}
	}

	/** Seam for a merchant (or a test) that resolves plans some other way. */
	protected PlanLookup newPlanLookup(final SubscriptionBillingConnector connector, final String storeUid)
	{
		return new CachingPlanLookup(connector, storeUid);
	}

	/** Reads each product's mapping once per check; anything that cannot be read counts as "no plan". */
	protected final class CachingPlanLookup implements PlanLookup
	{
		private final SubscriptionBillingConnector connector;
		private final String storeUid;
		private final Map<String, PlanRef> resolved = new HashMap<>();

		/** Public on a protected class: a constructor that is merely protected could not be called by a subclass of the service. */
		public CachingPlanLookup(final SubscriptionBillingConnector connector, final String storeUid)
		{
			this.connector = connector;
			this.storeUid = storeUid;
		}

		protected SubscriptionBillingConnector connector()
		{
			return connector;
		}

		protected String storeUid()
		{
			return storeUid;
		}

		@Override
		public PlanRef planOf(final String productCode)
		{
			if (StringUtils.isBlank(productCode))
			{
				return null;
			}
			if (!resolved.containsKey(productCode))
			{
				PlanRef plan = null;
				try
				{
					plan = connector.resolvePlan(new PlanResolutionRequest(productCode, storeUid, Map.of()));
				}
				catch (final PlanNotMappedException e)
				{
					// Not a subscription product.
				}
				catch (final BillingException | RuntimeException e)
				{
					LOG.debug("Could not resolve the plan of product '{}' in store '{}'", productCode, storeUid, e);
				}
				resolved.put(productCode, plan);
			}
			return resolved.get(productCode);
		}
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}

	public void setUserService(final UserService userService)
	{
		this.userService = userService;
	}

	public void setConnectorRegistry(final SubscriptionBillingConnectorRegistry connectorRegistry)
	{
		this.connectorRegistry = connectorRegistry;
	}

	public void setSubscriptionProductRule(final SubscriptionProductRule subscriptionProductRule)
	{
		this.subscriptionProductRule = subscriptionProductRule;
	}

	public void setCustomerSubscriptionService(final CustomerSubscriptionService customerSubscriptionService)
	{
		this.customerSubscriptionService = customerSubscriptionService;
	}

	public void setReconciliationService(final SubscriptionReconciliationService reconciliationService)
	{
		this.reconciliationService = reconciliationService;
	}

	public void setStrategies(final Map<DuplicateSubscriptionRule, DuplicateSubscriptionRuleStrategy> strategies)
	{
		this.strategies = strategies;
	}

	public void setStaleAfterMinutes(final int staleAfterMinutes)
	{
		this.staleAfterMinutes = staleAfterMinutes;
	}

	public void setPendingOrderWindowHours(final int pendingOrderWindowHours)
	{
		this.pendingOrderWindowHours = pendingOrderWindowHours;
	}

	/** How long one {@link Freshness#REFRESH} check may spend on the billing platform; see the class comment. */
	public void setRefreshBudgetMillis(final long refreshBudgetMillis)
	{
		this.refreshBudgetMillis = refreshBudgetMillis;
	}

	void setClock(final Clock clock)
	{
		this.clock = clock;
	}
}
