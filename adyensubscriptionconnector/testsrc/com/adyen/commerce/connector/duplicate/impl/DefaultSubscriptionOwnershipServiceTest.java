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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.adyen.commerce.connector.dto.BillingSubscriptionRef;
import com.adyen.commerce.connector.dto.NormalizedSubscription;
import com.adyen.commerce.connector.dto.NormalizedSubscriptionStatus;
import com.adyen.commerce.connector.dto.PlanRef;
import com.adyen.commerce.connector.dto.PlanResolutionRequest;
import com.adyen.commerce.connector.duplicate.BlockingSubscription;
import com.adyen.commerce.connector.duplicate.BlockingSubscription.Source;
import com.adyen.commerce.connector.duplicate.DuplicateCheckRequest;
import com.adyen.commerce.connector.duplicate.DuplicateCheckStage;
import com.adyen.commerce.connector.duplicate.Freshness;
import com.adyen.commerce.connector.duplicate.PlanLookup;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.enums.DuplicateSubscriptionRule;
import com.adyen.commerce.connector.exception.ConnectorNotConfiguredException;
import com.adyen.commerce.connector.exception.PlanNotMappedException;
import com.adyen.commerce.connector.exception.RetryableBillingException;
import com.adyen.commerce.connector.exception.SubscriptionProductUndecidableException;
import com.adyen.commerce.connector.model.BillingCustomerRefModel;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;
import com.adyen.commerce.connector.product.SubscriptionProductRule;
import com.adyen.commerce.connector.reconciliation.SubscriptionReconciliationService;
import com.adyen.commerce.connector.registry.SubscriptionBillingConnectorRegistry;
import com.adyen.commerce.connector.selfservice.CustomerSubscriptionService;
import com.adyen.commerce.connector.selfservice.SubscriptionLifecycleState;
import com.adyen.commerce.connector.selfservice.impl.DefaultCustomerSubscriptionService;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.enums.OrderStatus;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.payment.dto.TransactionStatus;
import de.hybris.platform.payment.enums.PaymentTransactionType;
import de.hybris.platform.payment.model.PaymentTransactionEntryModel;
import de.hybris.platform.payment.model.PaymentTransactionModel;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.search.SearchResult;
import de.hybris.platform.servicelayer.user.UserService;
import de.hybris.platform.store.BaseStoreModel;

/**
 * The ownership decision: which lifecycle states and rules make a purchase a duplicate, what a pending order
 * counts for, and what the billing platform may and may not change.
 *
 * <p>The state of a record is worked out by the real {@link DefaultCustomerSubscriptionService}, so the matrix
 * below pins this service against the lifecycle the My Subscriptions page shows, not against a stand-in.</p>
 */
@UnitTest
public class DefaultSubscriptionOwnershipServiceTest
{
	private static final BillingPlatform PLATFORM = BillingPlatform.valueOf("CHARGEBEE");
	private static final BillingPlatform OTHER_PLATFORM = BillingPlatform.valueOf("RECURLY");
	private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");
	private static final String STORE_UID = "electronics";
	private static final String PRODUCT = "GOLD-MONTHLY";
	private static final String PLAN = "gold-monthly-plan";

	private final DefaultCustomerSubscriptionService lifecycle = new DefaultCustomerSubscriptionService();

	private DefaultSubscriptionOwnershipService service;
	private FlexibleSearchService flexibleSearchService;
	private UserService userService;
	private SubscriptionBillingConnectorRegistry registry;
	private SubscriptionProductRule productRule;
	private CustomerSubscriptionService customerSubscriptionService;
	private SubscriptionReconciliationService reconciliation;
	private SubscriptionBillingConnector connector;
	private BaseStoreModel store;
	private CustomerModel customer;
	private ProductModel product;

	private final List<BillingSubscriptionRefModel> refs = new ArrayList<>();
	private final List<OrderModel> recentOrders = new ArrayList<>();
	private final List<OrderModel> ordersWithRef = new ArrayList<>();
	private final List<OrderModel> ordersDeadLettered = new ArrayList<>();
	private final List<FlexibleSearchQuery> queries = new ArrayList<>();
	private final Map<String, PlanRef> mappings = new java.util.HashMap<>();
	/** Why the service reported a degraded check, in order. */
	private final List<String> degradedReasons = new ArrayList<>();
	private Instant now = NOW;
	private PlanLookup lookupOverride;

	@Before
	public void setUp() throws Exception
	{
		// A model cannot be constructed without the platform; the service asks for its in-memory record here.
		service = new DefaultSubscriptionOwnershipService()
		{
			@Override
			protected BillingSubscriptionRefModel newTransientRef()
			{
				return statefulRef();
			}

			@Override
			protected PlanLookup newPlanLookup(final SubscriptionBillingConnector connector, final String storeUid)
			{
				return lookupOverride != null ? lookupOverride : super.newPlanLookup(connector, storeUid);
			}

			@Override
			protected void degraded(final DuplicateCheckRequest request, final DuplicateSubscriptionRule rule,
					final BillingPlatform platform, final String reason, final Exception cause)
			{
				degradedReasons.add(reason);
				super.degraded(request, rule, platform, reason, cause);
			}
		};
		flexibleSearchService = mock(FlexibleSearchService.class);
		userService = mock(UserService.class);
		registry = mock(SubscriptionBillingConnectorRegistry.class);
		productRule = mock(SubscriptionProductRule.class);
		customerSubscriptionService = mock(CustomerSubscriptionService.class);
		reconciliation = mock(SubscriptionReconciliationService.class);
		connector = mock(SubscriptionBillingConnector.class);
		store = mock(BaseStoreModel.class);
		customer = mock(CustomerModel.class);
		product = product(PRODUCT);

		service.setFlexibleSearchService(flexibleSearchService);
		service.setUserService(userService);
		service.setConnectorRegistry(registry);
		service.setSubscriptionProductRule(productRule);
		service.setCustomerSubscriptionService(customerSubscriptionService);
		service.setReconciliationService(reconciliation);
		service.setStrategies(Map.of(
				DuplicateSubscriptionRule.SAME_PRODUCT, new SameProductRuleStrategy(),
				DuplicateSubscriptionRule.SAME_PLAN, new SamePlanRuleStrategy(),
				DuplicateSubscriptionRule.SAME_PLAN_FAMILY, new SamePlanFamilyRuleStrategy()));
		service.setStaleAfterMinutes(15);
		service.setPendingOrderWindowHours(48);
		service.setRefreshBudgetMillis(5000);
		service.setClock(new Clock()
		{
			@Override
			public ZoneOffset getZone()
			{
				return ZoneOffset.UTC;
			}

			@Override
			public Clock withZone(final java.time.ZoneId zone)
			{
				return this;
			}

			@Override
			public Instant instant()
			{
				return now;
			}
		});

		when(store.getUid()).thenReturn(STORE_UID);
		when(store.getDuplicateSubscriptionRule()).thenReturn(DuplicateSubscriptionRule.SAME_PRODUCT);
		when(registry.getActiveConnector(store)).thenReturn(connector);
		when(connector.platform()).thenReturn(PLATFORM);
		when(productRule.isSubscriptionProduct(any(), any(), any())).thenReturn(true);
		when(userService.isAnonymousUser(any())).thenReturn(false);
		when(customerSubscriptionService.getSubscriptions(customer)).thenAnswer(invocation -> List.copyOf(refs));
		when(customerSubscriptionService.getState(any()))
				.thenAnswer(invocation -> lifecycle.getState(invocation.getArgument(0)));
		when(connector.resolvePlan(any(PlanResolutionRequest.class))).thenAnswer(invocation ->
		{
			final String code = invocation.<PlanResolutionRequest> getArgument(0).productCode();
			final PlanRef plan = mappings.get(code);
			if (plan == null)
			{
				throw new PlanNotMappedException("no mapping for " + code);
			}
			return plan;
		});
		mappings.put(PRODUCT, new PlanRef(PLAN, null));
		when(flexibleSearchService.search(any(FlexibleSearchQuery.class))).thenAnswer(invocation ->
		{
			final FlexibleSearchQuery query = invocation.getArgument(0);
			queries.add(query);
			final Object order = query.getQueryParameters().get("order");
			final List<Object> result = new ArrayList<>();
			if (query.getQuery().contains("FROM {Order}"))
			{
				result.addAll(recentOrders);
			}
			else if (query.getQuery().contains("FROM {BillingSubscriptionRef}") && ordersWithRef.contains(order))
			{
				result.add(order);
			}
			else if (query.getQuery().contains("FROM {BillingActivationAttempt}")
					&& "DEAD_LETTER".equals(query.getQueryParameters().get("status"))
					&& ordersDeadLettered.contains(order))
			{
				result.add(order);
			}
			final SearchResult<Object> searchResult = mock(SearchResult.class);
			when(searchResult.getResult()).thenReturn(result);
			return searchResult;
		});
	}

	// --- when nothing is asked ---

	@Test
	public void anAnonymousCustomerIsNeverBlocked() throws Exception
	{
		refs.add(ref(NormalizedSubscriptionStatus.ACTIVE));
		when(userService.isAnonymousUser(customer)).thenReturn(true);

		assertTrue(check(Freshness.LOCAL).isEmpty());
	}

	@Test
	public void aStoreWithTheCheckDisabledNeverBlocks() throws Exception
	{
		refs.add(ref(NormalizedSubscriptionStatus.ACTIVE));
		when(store.getDuplicateSubscriptionRule()).thenReturn(DuplicateSubscriptionRule.DISABLED);

		assertTrue(check(Freshness.REFRESH).isEmpty());
		verifyNoInteractions(registry, connector);
	}

	/** A store that predates the attribute has none stored, and is protected like a new one. */
	@Test
	public void aStoreWithoutARuleIsCheckedAsSameProduct() throws Exception
	{
		refs.add(ref(NormalizedSubscriptionStatus.ACTIVE));
		when(store.getDuplicateSubscriptionRule()).thenReturn(null);

		final BlockingSubscription blocking = check(Freshness.LOCAL).orElseThrow();

		assertEquals(DuplicateSubscriptionRule.SAME_PRODUCT, blocking.rule());
	}

	@Test
	public void aProductThatIsNotASubscriptionProductIsNeverBlocked() throws Exception
	{
		refs.add(ref(NormalizedSubscriptionStatus.ACTIVE));
		when(productRule.isSubscriptionProduct(any(), any(), any())).thenReturn(false);

		assertTrue(check(Freshness.LOCAL).isEmpty());
	}

	/** An undecidable product is "I do not know", which must not turn a customer away. */
	@Test
	public void anUndecidableProductFailsOpen() throws Exception
	{
		refs.add(ref(NormalizedSubscriptionStatus.ACTIVE));
		when(productRule.isSubscriptionProduct(any(), any(), any()))
				.thenThrow(new SubscriptionProductUndecidableException("cannot tell", null));

		assertTrue(check(Freshness.REFRESH).isEmpty());
	}

	@Test
	public void aStoreWithoutABillingPlatformSellsNoSubscriptions() throws Exception
	{
		refs.add(ref(NormalizedSubscriptionStatus.ACTIVE));
		when(registry.getActiveConnector(store)).thenThrow(new ConnectorNotConfiguredException("none"));

		assertTrue(check(Freshness.LOCAL).isEmpty());
	}

	// --- which lifecycle states block ---

	@Test
	public void everyStateThatStillServesTheCustomerBlocksAndEveryEndedOneDoesNot() throws Exception
	{
		final Date future = Date.from(NOW.plus(Duration.ofDays(10)));
		final Date past = Date.from(NOW.minus(Duration.ofDays(1)));
		final List<String> wrong = new ArrayList<>();

		final Object[][] table =
		{
				// status literal, cancelAtPeriodEnd, periodStart, periodEnd, platformConfirmed, blocks, state
				{ "ACTIVE", false, past, future, true, true, SubscriptionLifecycleState.ACTIVE },
				{ "ACTIVE", true, past, future, true, true, SubscriptionLifecycleState.ENDING },
				{ "ACTIVE", true, past, past, true, false, SubscriptionLifecycleState.ENDING },
				{ "PAST_DUE", false, past, future, true, true, SubscriptionLifecycleState.PAST_DUE },
				{ "PAST_DUE", true, past, future, true, true, SubscriptionLifecycleState.PAST_DUE_ENDING },
				{ "PAST_DUE", true, past, past, true, false, SubscriptionLifecycleState.PAST_DUE_ENDING },
				// No known end of the term: not proof that it ended, so the subscription keeps blocking.
				{ "ACTIVE", true, past, null, true, true, SubscriptionLifecycleState.ENDING },
				{ "PAST_DUE", true, past, null, true, true, SubscriptionLifecycleState.PAST_DUE_ENDING },
				{ "PENDING", false, future, future, true, true, SubscriptionLifecycleState.STARTING_SOON },
				{ "PENDING", false, past, future, true, true, SubscriptionLifecycleState.SETTING_UP },
				{ "PAUSED", false, past, future, true, true, SubscriptionLifecycleState.PAUSED },
				{ "ACTIVE", false, past, future, false, true, SubscriptionLifecycleState.SETTING_UP },
				{ "CANCELLED", false, past, past, true, false, SubscriptionLifecycleState.ENDED },
				{ "EXPIRED", false, past, past, true, false, SubscriptionLifecycleState.ENDED },
				{ "FAILED", false, past, past, true, false, SubscriptionLifecycleState.ENDED },
				{ "UNKNOWN", false, past, past, true, false, SubscriptionLifecycleState.UNAVAILABLE },
				{ "A_STATUS_FROM_A_LATER_RELEASE", false, past, past, true, false,
						SubscriptionLifecycleState.UNAVAILABLE },
				{ null, false, past, past, true, false, SubscriptionLifecycleState.UNAVAILABLE } };

		for (final Object[] row : table)
		{
			refs.clear();
			final BillingSubscriptionRefModel ref = ref(null);
			when(ref.getStatus()).thenReturn((String) row[0]);
			when(ref.getCancelAtPeriodEnd()).thenReturn((Boolean) row[1]);
			when(ref.getCurrentPeriodStart()).thenReturn((Date) row[2]);
			when(ref.getCurrentPeriodEnd()).thenReturn((Date) row[3]);
			when(ref.getPlatformUpdatedAt()).thenReturn((Boolean) row[4] ? past : null);
			refs.add(ref);

			final Optional<BlockingSubscription> blocking = check(Freshness.LOCAL);

			if (blocking.isPresent() != (Boolean) row[5])
			{
				wrong.add(row[0] + "/ending=" + row[1] + "/confirmed=" + row[4] + " blocks=" + blocking.isPresent());
			}
			else if ((Boolean) row[5] && blocking.get().state() != row[6])
			{
				wrong.add(row[0] + " reported as " + blocking.get().state() + " not " + row[6]);
			}
		}
		assertTrue(wrong.toString(), wrong.isEmpty());
	}

	@Test
	public void theBlockNamesTheSubscriptionTheRuleAndWhereItCameFrom() throws Exception
	{
		final BillingSubscriptionRefModel ref = ref(NormalizedSubscriptionStatus.ACTIVE);
		when(ref.getCode()).thenReturn("SUB-1234");
		refs.add(ref);

		final BlockingSubscription blocking = check(Freshness.LOCAL).orElseThrow();

		assertEquals(new BlockingSubscription(Source.EXISTING_SUBSCRIPTION, "SUB-1234",
				SubscriptionLifecycleState.ACTIVE, DuplicateSubscriptionRule.SAME_PRODUCT), blocking);
	}

	// --- which rule says "the same" ---

	@Test
	public void sameProductIgnoresAnotherProductOnTheSamePlan() throws Exception
	{
		refs.add(ref(NormalizedSubscriptionStatus.ACTIVE, "SILVER-MONTHLY", PLAN));

		assertTrue(check(Freshness.LOCAL).isEmpty());
	}

	/** A reference from before the product code was kept is still caught, by its plan. */
	@Test
	public void sameProductFallsBackToThePlanForAReferenceWithoutAProductCode() throws Exception
	{
		refs.add(ref(NormalizedSubscriptionStatus.ACTIVE, null, PLAN));

		assertTrue(check(Freshness.LOCAL).isPresent());
	}

	@Test
	public void samePlanBlocksAnotherProductOnTheSamePlan() throws Exception
	{
		when(store.getDuplicateSubscriptionRule()).thenReturn(DuplicateSubscriptionRule.SAME_PLAN);
		refs.add(ref(NormalizedSubscriptionStatus.ACTIVE, "SILVER-MONTHLY", PLAN));

		assertEquals(DuplicateSubscriptionRule.SAME_PLAN, check(Freshness.LOCAL).orElseThrow().rule());
	}

	@Test
	public void samePlanDoesNotBlockAnotherPlan() throws Exception
	{
		when(store.getDuplicateSubscriptionRule()).thenReturn(DuplicateSubscriptionRule.SAME_PLAN);
		refs.add(ref(NormalizedSubscriptionStatus.ACTIVE, "GOLD-YEARLY", "gold-yearly-plan"));

		assertTrue(check(Freshness.LOCAL).isEmpty());
	}

	/** A plan code means something on its own platform only. */
	@Test
	public void aPlanCodeOnAnotherPlatformIsNotComparedWithThisOne() throws Exception
	{
		when(store.getDuplicateSubscriptionRule()).thenReturn(DuplicateSubscriptionRule.SAME_PLAN);
		final BillingSubscriptionRefModel ref = ref(NormalizedSubscriptionStatus.ACTIVE, null, PLAN);
		when(ref.getPlatform()).thenReturn(OTHER_PLATFORM);
		refs.add(ref);

		assertTrue(check(Freshness.LOCAL).isEmpty());
	}

	@Test
	public void samePlanFamilyBlocksADifferentPlanInTheSameFamily() throws Exception
	{
		when(store.getDuplicateSubscriptionRule()).thenReturn(DuplicateSubscriptionRule.SAME_PLAN_FAMILY);
		mappings.put(PRODUCT, new PlanRef(PLAN, null, "gold"));
		mappings.put("GOLD-YEARLY", new PlanRef("gold-yearly-plan", null, "gold"));
		refs.add(ref(NormalizedSubscriptionStatus.ACTIVE, "GOLD-YEARLY", "gold-yearly-plan"));

		assertEquals(DuplicateSubscriptionRule.SAME_PLAN_FAMILY, check(Freshness.LOCAL).orElseThrow().rule());
	}

	@Test
	public void samePlanFamilyDoesNotBlockAPlanOfAnotherFamily() throws Exception
	{
		when(store.getDuplicateSubscriptionRule()).thenReturn(DuplicateSubscriptionRule.SAME_PLAN_FAMILY);
		mappings.put(PRODUCT, new PlanRef(PLAN, null, "gold"));
		mappings.put("NEWS-YEARLY", new PlanRef("news-plan", null, "news"));
		refs.add(ref(NormalizedSubscriptionStatus.ACTIVE, "NEWS-YEARLY", "news-plan"));

		assertTrue(check(Freshness.LOCAL).isEmpty());
	}

	/** Without a family the rule is SAME_PLAN: another plan does not block, the same plan does. */
	@Test
	public void samePlanFamilyWithoutAFamilyBehavesAsSamePlan() throws Exception
	{
		when(store.getDuplicateSubscriptionRule()).thenReturn(DuplicateSubscriptionRule.SAME_PLAN_FAMILY);
		mappings.put("GOLD-YEARLY", new PlanRef("gold-yearly-plan", null, null));
		refs.add(ref(NormalizedSubscriptionStatus.ACTIVE, "GOLD-YEARLY", "gold-yearly-plan"));
		assertTrue(check(Freshness.LOCAL).isEmpty());

		refs.clear();
		refs.add(ref(NormalizedSubscriptionStatus.ACTIVE, "SILVER-MONTHLY", PLAN));
		assertTrue(check(Freshness.LOCAL).isPresent());
	}

	@Test
	public void theCartOrOrderThePurchaseBelongsToNeverBlocksItself() throws Exception
	{
		final OrderModel current = mock(OrderModel.class);
		final BillingSubscriptionRefModel ref = ref(NormalizedSubscriptionStatus.ACTIVE);
		when(ref.getOrder()).thenReturn(current);
		refs.add(ref);

		assertTrue(check(Freshness.LOCAL, current).isEmpty());
		assertTrue(check(Freshness.LOCAL).isPresent());
	}

	// --- orders whose subscription does not exist yet ---

	@Test
	public void aRecentOrderForTheSameSubscriptionBlocksUntilItsSubscriptionExists() throws Exception
	{
		recentOrders.add(order(OrderStatus.PAYMENT_AUTHORIZED, PRODUCT));

		final BlockingSubscription blocking = check(Freshness.LOCAL).orElseThrow();

		assertEquals(Source.PENDING_ORDER, blocking.source());
		assertNull(blocking.subscriptionCode());
		assertEquals(SubscriptionLifecycleState.SETTING_UP, blocking.state());
	}

	@Test
	public void anOrderWhosePaymentFailedOrWasUndoneDoesNotBlock() throws Exception
	{
		final List<String> blocked = new ArrayList<>();
		for (final OrderStatus failed : List.of(OrderStatus.CANCELLED, OrderStatus.CANCELLING,
				OrderStatus.PAYMENT_NOT_AUTHORIZED, OrderStatus.PROCESSING_ERROR, OrderStatus.CHARGEBACK,
				OrderStatus.REJECTED, OrderStatus.CHECKED_INVALID))
		{
			recentOrders.clear();
			recentOrders.add(order(failed, PRODUCT));
			if (check(Freshness.LOCAL).isPresent())
			{
				blocked.add(failed.getCode());
			}
		}
		assertTrue("still blocking: " + blocked, blocked.isEmpty());

		// And the states on the way to success, or holding an authorised amount, still do.
		for (final OrderStatus live : List.of(OrderStatus.PAYMENT_PENDING, OrderStatus.PAYMENT_AUTHORIZED,
				OrderStatus.CREATED, OrderStatus.COMPLETED, OrderStatus.PAYMENT_AMOUNT_NOT_RESERVED,
				OrderStatus.PAYMENT_NOT_CAPTURED))
		{
			recentOrders.clear();
			recentOrders.add(order(live, PRODUCT));
			assertTrue(live.getCode(), check(Freshness.LOCAL).isPresent());
		}
	}

	/**
	 * Placing the pending order turns the cart into an order before the shopper acts; cancelling the 3DS or the
	 * redirect only copies the entries into a new cart. That abandoned order must not block its own retry.
	 */
	@Test
	public void anOrderTheShopperWalkedAwayFromAtA3dsOrRedirectDoesNotBlockTheRestoredCart() throws Exception
	{
		final List<String> blocked = new ArrayList<>();
		for (final String resultCode : List.of("RedirectShopper", "ChallengeShopper", "IdentifyShopper",
				"redirectshopper"))
		{
			recentOrders.clear();
			recentOrders.add(pendingOrder(resultCode));
			if (check(Freshness.LOCAL).isEmpty())
			{
				continue;
			}
			blocked.add(resultCode);
		}
		assertTrue("still blocking: " + blocked, blocked.isEmpty());
	}

	/**
	 * The shopper finished and Adyen has not settled it yet: the money is on its way, so it blocks. That includes a
	 * voucher awaiting payment (PresentToShopper).
	 */
	@Test
	public void aPendingOrderTheShopperFinishedKeepsBlocking() throws Exception
	{
		for (final String resultCode : new String[] { "Pending", "Received", "Authorised", "PresentToShopper", "presenttoshopper", "", null })
		{
			recentOrders.clear();
			recentOrders.add(pendingOrder(resultCode));
			assertTrue(String.valueOf(resultCode), check(Freshness.LOCAL).isPresent());
		}
	}

	@Test
	public void anOrderWithAnAcceptedAuthorisationBlocksWhateverItsResultCodeSays() throws Exception
	{
		final OrderModel order = pendingOrder("RedirectShopper");
		withAuthorisation(order, TransactionStatus.ACCEPTED);
		recentOrders.add(order);

		assertEquals(Source.PENDING_ORDER, check(Freshness.LOCAL).orElseThrow().source());
	}

	@Test
	public void aRejectedAuthorisationDoesNotKeepAnAbandonedOrderBlocking() throws Exception
	{
		final OrderModel order = pendingOrder("RedirectShopper");
		withAuthorisation(order, TransactionStatus.REJECTED);
		recentOrders.add(order);

		assertTrue(check(Freshness.LOCAL).isEmpty());
	}

	/** Its process ended in error, but an authorisation already taken is still held at Adyen. */
	@Test
	public void anInvalidOrderBlocksOnlyWhileAnAuthorisationIsHeld() throws Exception
	{
		final OrderModel order = order(OrderStatus.CHECKED_INVALID, PRODUCT);
		recentOrders.add(order);
		assertTrue(check(Freshness.LOCAL).isEmpty());

		withAuthorisation(order, TransactionStatus.ACCEPTED);
		assertTrue(check(Freshness.LOCAL).isPresent());
	}

	/** PROCESSING_ERROR is also set on an order whose authorisation is held (a CAPTURE notification that threw). */
	@Test
	public void anOrderInProcessingErrorBlocksOnlyWhileAnAuthorisationIsHeld() throws Exception
	{
		final OrderModel order = order(OrderStatus.PROCESSING_ERROR, PRODUCT);
		recentOrders.add(order);
		assertTrue(check(Freshness.LOCAL).isEmpty());

		withAuthorisation(order, TransactionStatus.ACCEPTED);
		assertTrue(check(Freshness.LOCAL).isPresent());
	}

	@Test
	public void anOrderThatAlreadyHasItsSubscriptionIsLeftToTheSubscription() throws Exception
	{
		final OrderModel order = order(OrderStatus.PAYMENT_AUTHORIZED, PRODUCT);
		recentOrders.add(order);
		ordersWithRef.add(order);

		assertTrue(check(Freshness.LOCAL).isEmpty());
	}

	/** A dead letter gave up: the shopper paid and got nothing, and must be able to try again. */
	@Test
	public void anOrderWhoseActivationWasDeadLetteredDoesNotBlock() throws Exception
	{
		final OrderModel order = order(OrderStatus.PAYMENT_AUTHORIZED, PRODUCT);
		recentOrders.add(order);
		ordersDeadLettered.add(order);

		assertTrue(check(Freshness.LOCAL).isEmpty());
	}

	@Test
	public void theOrderThePurchaseBelongsToNeverBlocksItself() throws Exception
	{
		final OrderModel pending = order(OrderStatus.PAYMENT_PENDING, PRODUCT);
		recentOrders.add(pending);

		assertTrue(check(Freshness.LOCAL, pending).isEmpty());
	}

	@Test
	public void anOrderForAnotherProductDoesNotBlock() throws Exception
	{
		mappings.put("MUG", new PlanRef("mug-plan", null));
		recentOrders.add(order(OrderStatus.PAYMENT_AUTHORIZED, "MUG"));

		assertTrue(check(Freshness.LOCAL).isEmpty());
	}

	@Test
	public void onlyOrdersFromTheLastWindowInThisStoreAreLookedAt() throws Exception
	{
		check(Freshness.LOCAL);

		final FlexibleSearchQuery query = queries.stream().filter(q -> q.getQuery().contains("FROM {Order}"))
				.findFirst().orElseThrow();
		assertEquals(store, query.getQueryParameters().get("store"));
		assertEquals(customer, query.getQueryParameters().get("customer"));
		assertEquals(Date.from(NOW.minus(Duration.ofHours(48))), query.getQueryParameters().get("since"));
		assertTrue(query.getQuery().contains("{versionID} IS NULL"));
	}

	// --- freshness ---

	@Test
	public void localNeverCallsTheBillingPlatform() throws Exception
	{
		final BillingSubscriptionRefModel stale = ref(NormalizedSubscriptionStatus.ACTIVE);
		when(stale.getLastSyncedAt()).thenReturn(null);
		refs.add(stale);

		assertTrue(check(Freshness.LOCAL).isPresent());

		verifyNoInteractions(reconciliation);
		verify(connector, never()).listCustomerSubscriptions(any(), any());
		verify(connector, never()).fetchSubscription(any());
	}

	@Test
	public void refreshReReadsAStaleSubscriptionAndTrustsWhatThePlatformSays() throws Exception
	{
		final BillingSubscriptionRefModel ref = ref(NormalizedSubscriptionStatus.ACTIVE);
		when(ref.getLastSyncedAt()).thenReturn(Date.from(NOW.minus(Duration.ofMinutes(16))));
		refs.add(ref);
		// The platform says it has ended since.
		doAnswer(invocation ->
		{
			when(ref.getStatus()).thenReturn("EXPIRED");
			return null;
		}).when(reconciliation).reconcile(ref);

		assertTrue(check(Freshness.REFRESH).isEmpty());
		verify(reconciliation).reconcile(ref);
	}

	@Test
	public void refreshLeavesAFreshSubscriptionAlone() throws Exception
	{
		final BillingSubscriptionRefModel ref = ref(NormalizedSubscriptionStatus.ACTIVE);
		when(ref.getLastSyncedAt()).thenReturn(Date.from(NOW.minus(Duration.ofMinutes(14))));
		refs.add(ref);

		assertTrue(check(Freshness.REFRESH).isPresent());
		verify(reconciliation, never()).reconcile(any());
	}

	/** Cleared after a failed post-cancel read: it means "read me", not "I do not matter". */
	@Test
	public void aMissingLastSyncedAtCountsAsStaleNotAsIrrelevant() throws Exception
	{
		final BillingSubscriptionRefModel ref = ref(NormalizedSubscriptionStatus.ACTIVE);
		when(ref.getLastSyncedAt()).thenReturn(null);
		refs.add(ref);

		assertTrue(check(Freshness.REFRESH).isPresent());
		verify(reconciliation).reconcile(ref);
	}

	@Test
	public void aFailedRefreshDecidesOnWhatIsKnownLocally() throws Exception
	{
		final BillingSubscriptionRefModel ref = ref(NormalizedSubscriptionStatus.ACTIVE);
		when(ref.getLastSyncedAt()).thenReturn(null);
		refs.add(ref);
		when(reconciliation.reconcile(ref)).thenThrow(new RetryableBillingException("down"));

		assertEquals(Source.EXISTING_SUBSCRIPTION, check(Freshness.REFRESH).orElseThrow().source());
	}

	@Test
	public void aSubscriptionOnlyThePlatformKnowsBlocksWithoutBeingSaved() throws Exception
	{
		givenPlatformCustomer("ext-cust");
		when(connector.listCustomerSubscriptions(eq("ext-cust"), any())).thenReturn(List.of(
				snapshot("sub-9", NormalizedSubscriptionStatus.ACTIVE, PLAN)));

		final BlockingSubscription blocking = check(Freshness.REFRESH).orElseThrow();

		assertEquals(Source.PLATFORM, blocking.source());
		assertNull(blocking.subscriptionCode());
		assertEquals(SubscriptionLifecycleState.ACTIVE, blocking.state());
		verify(reconciliation, never()).reconcile(any());
	}

	@Test
	public void aPlatformSubscriptionThatHasEndedOrIsAnotherPlanDoesNotBlock() throws Exception
	{
		givenPlatformCustomer("ext-cust");
		when(connector.listCustomerSubscriptions(eq("ext-cust"), any())).thenReturn(List.of(
				snapshot("sub-1", NormalizedSubscriptionStatus.EXPIRED, PLAN),
				snapshot("sub-2", NormalizedSubscriptionStatus.ACTIVE, "another-plan")));

		assertTrue(check(Freshness.REFRESH).isEmpty());
	}

	@Test
	public void aPlatformSubscriptionThatIsAlreadyOurRecordIsReportedWithItsCode() throws Exception
	{
		givenPlatformCustomer("ext-cust");
		final BillingSubscriptionRefModel known = ref(NormalizedSubscriptionStatus.EXPIRED);
		when(known.getExternalSubscriptionId()).thenReturn("sub-1");
		when(known.getCode()).thenReturn("SUB-77");
		refs.add(known);
		when(connector.listCustomerSubscriptions(eq("ext-cust"), any())).thenReturn(List.of(
				snapshot("sub-1", NormalizedSubscriptionStatus.ACTIVE, PLAN)));

		final BlockingSubscription blocking = check(Freshness.REFRESH).orElseThrow();

		assertEquals(Source.EXISTING_SUBSCRIPTION, blocking.source());
		assertEquals("SUB-77", blocking.subscriptionCode());
	}

	@Test
	public void aFailedListingDecidesOnWhatIsKnownLocally() throws Exception
	{
		givenPlatformCustomer("ext-cust");
		when(connector.listCustomerSubscriptions(eq("ext-cust"), any())).thenThrow(new RetryableBillingException("timeout"));

		assertTrue(check(Freshness.REFRESH).isEmpty());

		refs.add(ref(NormalizedSubscriptionStatus.ACTIVE));
		assertTrue(check(Freshness.REFRESH).isPresent());
	}

	@Test
	public void anUnexpectedErrorFromTheListingIsAlsoFailOpen() throws Exception
	{
		givenPlatformCustomer("ext-cust");
		when(connector.listCustomerSubscriptions(eq("ext-cust"), any())).thenThrow(new IllegalStateException("bug"));

		assertTrue(check(Freshness.REFRESH).isEmpty());
	}

	@Test
	public void aCustomerThePlatformDoesNotKnowIsNotListed() throws Exception
	{
		check(Freshness.REFRESH);

		verify(connector, never()).listCustomerSubscriptions(any(), any());
	}

	@Test
	public void aPlatformSubscriptionOfTheOrderBeingPaidForDoesNotBlockItself() throws Exception
	{
		givenPlatformCustomer("ext-cust");
		final OrderModel current = mock(OrderModel.class);
		final BillingSubscriptionRefModel known = ref(NormalizedSubscriptionStatus.EXPIRED);
		when(known.getExternalSubscriptionId()).thenReturn("sub-1");
		when(known.getOrder()).thenReturn(current);
		refs.add(known);
		when(connector.listCustomerSubscriptions(eq("ext-cust"), any())).thenReturn(List.of(
				snapshot("sub-1", NormalizedSubscriptionStatus.ACTIVE, PLAN)));

		assertTrue(check(Freshness.REFRESH, current).isEmpty());
	}

	// --- the deadline ---

	@Test
	public void refreshStopsReReadingOnceTheBudgetIsSpentAndDecidesOnWhatItKnows() throws Exception
	{
		final BillingSubscriptionRefModel first = staleActiveRef();
		final BillingSubscriptionRefModel second = staleActiveRef();
		refs.add(first);
		refs.add(second);
		// The first re-read is slow: it eats the whole budget.
		doAnswer(invocation ->
		{
			now = now.plusSeconds(6);
			return null;
		}).when(reconciliation).reconcile(first);

		assertTrue(check(Freshness.REFRESH).isPresent());

		verify(reconciliation).reconcile(first);
		verify(reconciliation, never()).reconcile(second);
		assertEquals(List.of("budget_exhausted"), degradedReasons);
	}

	@Test
	public void refreshDoesNotReReadAnythingAfterTheFirstRefreshFailed() throws Exception
	{
		final BillingSubscriptionRefModel first = staleActiveRef();
		final BillingSubscriptionRefModel second = staleActiveRef();
		refs.add(first);
		refs.add(second);
		when(reconciliation.reconcile(first)).thenThrow(new RetryableBillingException("down"));

		assertTrue(check(Freshness.REFRESH).isPresent());

		verify(reconciliation, never()).reconcile(second);
		assertEquals(List.of("refresh_failed"), degradedReasons);
	}

	@Test
	public void refreshDoesNotListWhenTheReReadsHaveSpentTheBudget() throws Exception
	{
		givenPlatformCustomer("ext-cust");
		final BillingSubscriptionRefModel stale = staleActiveRef();
		refs.add(stale);
		// The re-read is slow, and finds the subscription over: nothing local blocks, but no time is left to list.
		doAnswer(invocation ->
		{
			now = now.plusSeconds(6);
			when(stale.getStatus()).thenReturn("EXPIRED");
			return null;
		}).when(reconciliation).reconcile(stale);

		assertTrue(check(Freshness.REFRESH).isEmpty());

		verify(connector, never()).listCustomerSubscriptions(any(), any());
		assertEquals(List.of("budget_exhausted"), degradedReasons);
	}

	/** The connector pages on its own; it is told when to stop. */
	@Test
	public void theListingIsHandedTheDeadlineSoItCanStopBetweenPages() throws Exception
	{
		givenPlatformCustomer("ext-cust");
		when(connector.listCustomerSubscriptions(eq("ext-cust"), any())).thenReturn(List.of());

		check(Freshness.REFRESH);

		final ArgumentCaptor<BooleanSupplier> mayContinue = ArgumentCaptor.forClass(BooleanSupplier.class);
		verify(connector).listCustomerSubscriptions(eq("ext-cust"), mayContinue.capture());
		assertTrue(mayContinue.getValue().getAsBoolean());
		now = now.plusSeconds(6);
		assertTrue(!mayContinue.getValue().getAsBoolean());
	}

	@Test
	public void aListingCutShortByTheDeadlineStillCountsWhatItReturned() throws Exception
	{
		givenPlatformCustomer("ext-cust");
		when(connector.listCustomerSubscriptions(eq("ext-cust"), any())).thenAnswer(invocation ->
		{
			now = now.plusSeconds(6);
			return List.of(snapshot("sub-9", NormalizedSubscriptionStatus.ACTIVE, PLAN));
		});

		assertEquals(Source.PLATFORM, check(Freshness.REFRESH).orElseThrow().source());
		assertEquals(List.of("budget_exhausted"), degradedReasons);
	}

	@Test
	public void refreshAsksForTheCustomersSubscriptionsOnce() throws Exception
	{
		givenPlatformCustomer("ext-cust");
		when(connector.listCustomerSubscriptions(eq("ext-cust"), any())).thenReturn(List.of());

		check(Freshness.REFRESH);

		verify(customerSubscriptionService, times(1)).getSubscriptions(customer);
	}

	// --- refresh decides as the cart does ---

	/** Gold and silver sit on one plan; owning silver says nothing about gold when the rule is the product. */
	@Test
	public void refreshOfASnapshotOfOurRecordComparesTheProductLikeTheCartDoes() throws Exception
	{
		givenPlatformCustomer("ext-cust");
		final BillingSubscriptionRefModel silver = ref(NormalizedSubscriptionStatus.EXPIRED, "SILVER-MONTHLY", PLAN);
		when(silver.getExternalSubscriptionId()).thenReturn("sub-1");
		refs.add(silver);
		when(connector.listCustomerSubscriptions(eq("ext-cust"), any())).thenReturn(List.of(
				snapshot("sub-1", NormalizedSubscriptionStatus.ACTIVE, PLAN)));

		when(store.getDuplicateSubscriptionRule()).thenReturn(DuplicateSubscriptionRule.SAME_PRODUCT);
		assertTrue(check(Freshness.REFRESH).isEmpty());

		when(store.getDuplicateSubscriptionRule()).thenReturn(DuplicateSubscriptionRule.SAME_PLAN);
		assertEquals(Source.EXISTING_SUBSCRIPTION, check(Freshness.REFRESH).orElseThrow().source());
	}

	@Test
	public void refreshOfASnapshotOfOurRecordComparesTheFamilyOfItsProduct() throws Exception
	{
		givenPlatformCustomer("ext-cust");
		when(store.getDuplicateSubscriptionRule()).thenReturn(DuplicateSubscriptionRule.SAME_PLAN_FAMILY);
		mappings.put(PRODUCT, new PlanRef(PLAN, null, "gold"));
		mappings.put("GOLD-YEARLY", new PlanRef("gold-yearly-plan", null, "gold"));
		final BillingSubscriptionRefModel yearly = ref(NormalizedSubscriptionStatus.EXPIRED, "GOLD-YEARLY",
				"gold-yearly-plan");
		when(yearly.getExternalSubscriptionId()).thenReturn("sub-1");
		refs.add(yearly);
		when(connector.listCustomerSubscriptions(eq("ext-cust"), any())).thenReturn(List.of(
				snapshot("sub-1", NormalizedSubscriptionStatus.ACTIVE, "gold-yearly-plan")));

		assertEquals(Source.EXISTING_SUBSCRIPTION, check(Freshness.REFRESH).orElseThrow().source());
	}

	/** A re-read can move the subscription to another plan; a record that no longer matches must not block. */
	@Test
	public void aRecordIsMatchedAgainAfterItWasReRead() throws Exception
	{
		when(store.getDuplicateSubscriptionRule()).thenReturn(DuplicateSubscriptionRule.SAME_PLAN);
		final BillingSubscriptionRefModel ref = staleActiveRef();
		refs.add(ref);
		doAnswer(invocation ->
		{
			when(ref.getPlanCode()).thenReturn("another-plan");
			return null;
		}).when(reconciliation).reconcile(ref);

		assertTrue(check(Freshness.REFRESH).isEmpty());
		verify(reconciliation).reconcile(ref);
	}

	@Test
	public void thePlanLookupCanBeReplacedByAMerchant() throws Exception
	{
		when(store.getDuplicateSubscriptionRule()).thenReturn(DuplicateSubscriptionRule.SAME_PLAN);
		lookupOverride = code -> "GOLD-MONTHLY".equals(code) || "SILVER-MONTHLY".equals(code)
				? new PlanRef("shared", null)
				: null;
		refs.add(ref(NormalizedSubscriptionStatus.ACTIVE, "SILVER-MONTHLY", null));

		assertTrue(check(Freshness.LOCAL).isPresent());
		verify(connector, never()).resolvePlan(any());
	}

	/** The stage is for the log only: leaving it out changes nothing about the decision. */
	@Test
	public void theStageDoesNotChangeTheDecision() throws Exception
	{
		refs.add(ref(NormalizedSubscriptionStatus.ACTIVE));

		final Optional<BlockingSubscription> withStage = service.findBlocking(new DuplicateCheckRequest(customer,
				product, store, Freshness.LOCAL, null, DuplicateCheckStage.CART_VALIDATION));
		final Optional<BlockingSubscription> withoutStage = service
				.findBlocking(new DuplicateCheckRequest(customer, product, store, Freshness.LOCAL, null, null));

		assertTrue(withStage.isPresent());
		assertEquals(withStage, withoutStage);
	}

	// --- fixtures ---

	private Optional<BlockingSubscription> check(final Freshness freshness)
	{
		return check(freshness, null);
	}

	private Optional<BlockingSubscription> check(final Freshness freshness, final OrderModel excluded)
	{
		return service.findBlocking(
				new DuplicateCheckRequest(customer, product, store, freshness, excluded, DuplicateCheckStage.PRE_AUTH));
	}

	/** A subscription that serves, whose last sync is older than the staleness threshold. */
	private BillingSubscriptionRefModel staleActiveRef()
	{
		final BillingSubscriptionRefModel ref = ref(NormalizedSubscriptionStatus.ACTIVE);
		when(ref.getLastSyncedAt()).thenReturn(Date.from(NOW.minus(Duration.ofMinutes(16))));
		return ref;
	}

	/** What placing a pending order leaves behind: PAYMENT_PENDING and the Adyen result code in the status info. */
	private OrderModel pendingOrder(final String resultCode)
	{
		final OrderModel order = order(OrderStatus.PAYMENT_PENDING, PRODUCT);
		when(order.getStatusInfo()).thenReturn(resultCode);
		return order;
	}

	private void withAuthorisation(final OrderModel order, final TransactionStatus status)
	{
		final PaymentTransactionEntryModel entry = mock(PaymentTransactionEntryModel.class);
		when(entry.getType()).thenReturn(PaymentTransactionType.AUTHORIZATION);
		when(entry.getTransactionStatus()).thenReturn(status.name());
		final PaymentTransactionModel transaction = mock(PaymentTransactionModel.class);
		when(transaction.getEntries()).thenReturn(List.of(entry));
		when(order.getPaymentTransactions()).thenReturn(List.of(transaction));
	}

	private void givenPlatformCustomer(final String externalId)
	{
		final BillingCustomerRefModel customerRef = mock(BillingCustomerRefModel.class);
		when(customerRef.getPlatform()).thenReturn(PLATFORM);
		when(customerRef.getExternalId()).thenReturn(externalId);
		when(customer.getBillingCustomerRefs()).thenReturn(List.of(customerRef));
	}

	private static NormalizedSubscription snapshot(final String id, final NormalizedSubscriptionStatus status,
			final String plan)
	{
		return new NormalizedSubscription(new BillingSubscriptionRef(PLATFORM, id), status, plan, 1,
				NOW.minus(Duration.ofDays(5)), NOW.plus(Duration.ofDays(25)), false, NOW.minus(Duration.ofDays(1)));
	}

	private BillingSubscriptionRefModel ref(final NormalizedSubscriptionStatus status)
	{
		return ref(status, PRODUCT, PLAN);
	}

	/** A confirmed, freshly synced record that is mid-term; tests change what they care about. */
	private BillingSubscriptionRefModel ref(final NormalizedSubscriptionStatus status, final String productCode,
			final String planCode)
	{
		final BillingSubscriptionRefModel ref = mock(BillingSubscriptionRefModel.class);
		when(ref.getPlatform()).thenReturn(PLATFORM);
		when(ref.getStatus()).thenReturn(status == null ? null : status.name());
		when(ref.getProductCode()).thenReturn(productCode);
		when(ref.getPlanCode()).thenReturn(planCode);
		when(ref.getCancelAtPeriodEnd()).thenReturn(Boolean.FALSE);
		when(ref.getCurrentPeriodStart()).thenReturn(Date.from(NOW.minus(Duration.ofDays(5))));
		when(ref.getCurrentPeriodEnd()).thenReturn(Date.from(NOW.plus(Duration.ofDays(25))));
		when(ref.getPlatformUpdatedAt()).thenReturn(Date.from(NOW.minus(Duration.ofDays(1))));
		when(ref.getLastSyncedAt()).thenReturn(Date.from(NOW.minus(Duration.ofMinutes(1))));
		when(ref.getExternalSubscriptionId()).thenReturn("ext-" + System.identityHashCode(ref));
		return ref;
	}

	/** A record that remembers what the service sets on it, as a real model would. */
	private BillingSubscriptionRefModel statefulRef()
	{
		final BillingSubscriptionRefModel ref = mock(BillingSubscriptionRefModel.class);
		doAnswer(invocation ->
		{
			when(ref.getStatus()).thenReturn(invocation.<String> getArgument(0));
			return null;
		}).when(ref).setStatus(any());
		doAnswer(invocation ->
		{
			when(ref.getCancelAtPeriodEnd()).thenReturn(invocation.<Boolean> getArgument(0));
			return null;
		}).when(ref).setCancelAtPeriodEnd(any());
		doAnswer(invocation ->
		{
			when(ref.getCurrentPeriodStart()).thenReturn(invocation.<Date> getArgument(0));
			return null;
		}).when(ref).setCurrentPeriodStart(any());
		doAnswer(invocation ->
		{
			when(ref.getCurrentPeriodEnd()).thenReturn(invocation.<Date> getArgument(0));
			return null;
		}).when(ref).setCurrentPeriodEnd(any());
		doAnswer(invocation ->
		{
			when(ref.getPlatformUpdatedAt()).thenReturn(invocation.<Date> getArgument(0));
			return null;
		}).when(ref).setPlatformUpdatedAt(any());
		return ref;
	}

	private ProductModel product(final String code)
	{
		final ProductModel model = mock(ProductModel.class);
		when(model.getCode()).thenReturn(code);
		return model;
	}

	private OrderModel order(final OrderStatus status, final String... productCodes)
	{
		final OrderModel order = mock(OrderModel.class);
		when(order.getStatus()).thenReturn(status);
		final List<AbstractOrderEntryModel> entries = new ArrayList<>();
		for (final String code : productCodes)
		{
			final ProductModel entryProduct = product(code);
			final AbstractOrderEntryModel entry = mock(AbstractOrderEntryModel.class);
			when(entry.getProduct()).thenReturn(entryProduct);
			entries.add(entry);
		}
		when(order.getEntries()).thenReturn(entries);
		return order;
	}
}
