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
package com.adyen.commerce.connector.activation.impl;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adyen.commerce.connector.context.SubscriptionBaseStoreSelectorStrategy;
import com.adyen.commerce.connector.dto.CancellationTiming;
import com.adyen.commerce.connector.dto.NormalizedSubscriptionStatus;
import com.adyen.commerce.connector.dto.SubscriptionCancellation;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.BillingException;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;
import com.adyen.commerce.connector.service.SubscriptionBillingService;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.search.SearchResult;
import de.hybris.platform.servicelayer.session.SessionExecutionBody;
import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.site.BaseSiteService;
import de.hybris.platform.store.BaseStoreModel;

/**
 * What happens to a subscription when the order that started it is cancelled.
 */
@UnitTest
public class DefaultSubscriptionOrderCancellationServiceTest
{
	@Mock
	private SubscriptionBillingService subscriptionBillingService;
	@Mock
	private FlexibleSearchService flexibleSearchService;
	@Mock
	private SessionService sessionService;
	@Mock
	private BaseSiteService baseSiteService;
	@Mock
	private OrderModel order;
	@Mock
	private BaseStoreModel store;

	private DefaultSubscriptionOrderCancellationService service;

	@Before
	public void setUp()
	{
		MockitoAnnotations.openMocks(this);

		service = new DefaultSubscriptionOrderCancellationService();
		service.setSubscriptionBillingService(subscriptionBillingService);
		service.setFlexibleSearchService(flexibleSearchService);
		service.setSessionService(sessionService);
		service.setBaseSiteService(baseSiteService);

		when(order.getCode()).thenReturn("order-1");
		when(order.getStore()).thenReturn(store);

		// The local view is this class's own plumbing, not a collaborator to assert on: the body runs
		// inline so every test exercises it.
		when(sessionService.executeInLocalViewWithParams(any(), any(SessionExecutionBody.class)))
				.thenAnswer(invocation -> {
					invocation.<SessionExecutionBody> getArgument(1).execute();
					return null;
				});
	}

	@Test
	public void asksForTheCancellationToTakeEffectAtTheEndOfThePaidPeriod() throws Exception
	{
		givenSubscriptions(active());

		service.cancelSubscriptionFor(order);

		final ArgumentCaptor<SubscriptionCancellation> asked = ArgumentCaptor
				.forClass(SubscriptionCancellation.class);
		verify(subscriptionBillingService).cancel(any(), asked.capture());
		assertEquals("cancelling an order must never forfeit a period the shopper has already paid for",
				CancellationTiming.AT_PERIOD_END, asked.getValue().timing());
	}

	/**
	 * The connectors read their credentials from the base store in the session, and this runs on a listener
	 * thread carrying whatever store the publishing request left behind, so without the order's own store in
	 * context the cancellation goes to another store's billing account.
	 */
	@Test
	public void putsTheOrdersOwnStoreInContextBeforeCancelling() throws Exception
	{
		givenSubscriptions(active());

		service.cancelSubscriptionFor(order);

		final ArgumentCaptor<java.util.Map<String, Object>> params = ArgumentCaptor.forClass(java.util.Map.class);
		verify(sessionService).executeInLocalViewWithParams(params.capture(), any(SessionExecutionBody.class));
		assertEquals(store,
				params.getValue().get(SubscriptionBaseStoreSelectorStrategy.CURRENT_SUBSCRIPTION_BASE_STORE));
	}

	/**
	 * An order can be announced cancelled more than once, and on Chargebee a second cancellation is a real
	 * API call — that adapter sends no idempotency key.
	 */
	@Test
	public void leavesASubscriptionAlreadySetToStopAlone() throws Exception
	{
		final BillingSubscriptionRefModel ref = active();
		when(ref.getCancelAtPeriodEnd()).thenReturn(Boolean.TRUE);
		givenSubscriptions(ref);

		service.cancelSubscriptionFor(order);

		verify(subscriptionBillingService, never()).cancel(any(), any());
	}

	@Test
	public void leavesASubscriptionThatHasAlreadyEndedAlone() throws Exception
	{
		final BillingSubscriptionRefModel ref = active();
		when(ref.getStatus()).thenReturn(NormalizedSubscriptionStatus.EXPIRED.name());
		givenSubscriptions(ref);

		service.cancelSubscriptionFor(order);

		verify(subscriptionBillingService, never()).cancel(any(), any());
	}

	/**
	 * UNKNOWN means the last platform read could not be interpreted, not that the subscription has ended.
	 * Reading it as "already over" would leave a live subscription billing a cancelled order.
	 */
	@Test
	public void stillCancelsWhenTheLastKnownStatusCouldNotBeInterpreted() throws Exception
	{
		final BillingSubscriptionRefModel ref = active();
		when(ref.getStatus()).thenReturn(NormalizedSubscriptionStatus.UNKNOWN.name());
		givenSubscriptions(ref);

		service.cancelSubscriptionFor(order);

		verify(subscriptionBillingService).cancel(any(), any());
	}

	/**
	 * Uniqueness is on (order, platform), so an order whose store was switched between billing platforms can
	 * carry one reference per platform. Stopping only the first would leave the other billing.
	 */
	@Test
	public void stopsEverySubscriptionTheOrderStarted() throws Exception
	{
		givenSubscriptions(active(), active());

		service.cancelSubscriptionFor(order);

		verify(subscriptionBillingService, times(2)).cancel(any(), any());
	}

	/**
	 * The caller is in the middle of cancelling an order. A billing platform that is down must not turn that
	 * into a failed cancellation.
	 */
	@Test
	public void neverLetsAPlatformFailureReachTheOrderCancellation() throws Exception
	{
		givenSubscriptions(active());
		doThrow(new BillingException("Chargebee is down")).when(subscriptionBillingService).cancel(any(), any());

		service.cancelSubscriptionFor(order);
	}

	@Test
	public void neverLetsAMissingStoreReachTheOrderCancellation() throws Exception
	{
		givenSubscriptions(active());
		when(order.getStore()).thenReturn(null);

		service.cancelSubscriptionFor(order);

		verify(subscriptionBillingService, never()).cancel(any(), any());
	}

	@Test
	public void doesNothingForAnOrderThatStartedNoSubscription() throws Exception
	{
		givenSubscriptions();

		service.cancelSubscriptionFor(order);

		verify(subscriptionBillingService, never()).cancel(any(), any());
	}

	@Test
	public void toleratesAnAnnouncementWithoutAnOrder() throws Exception
	{
		service.cancelSubscriptionFor(null);

		verify(subscriptionBillingService, never()).cancel(any(), any());
	}

	@SuppressWarnings("unchecked")
	private void givenSubscriptions(final BillingSubscriptionRefModel... refs)
	{
		final SearchResult<BillingSubscriptionRefModel> result = mock(SearchResult.class);
		when(result.getResult()).thenReturn(List.of(refs));
		when(flexibleSearchService.<BillingSubscriptionRefModel> search(any(FlexibleSearchQuery.class)))
				.thenReturn(result);
	}

	private static BillingSubscriptionRefModel active()
	{
		final BillingSubscriptionRefModel ref = mock(BillingSubscriptionRefModel.class);
		when(ref.getPlatform()).thenReturn(BillingPlatform.CHARGEBEE);
		when(ref.getExternalSubscriptionId()).thenReturn("sub-1");
		when(ref.getStatus()).thenReturn(NormalizedSubscriptionStatus.ACTIVE.name());
		return ref;
	}
}
