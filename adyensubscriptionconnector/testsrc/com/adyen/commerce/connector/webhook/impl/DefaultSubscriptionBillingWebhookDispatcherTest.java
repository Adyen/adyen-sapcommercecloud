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
package com.adyen.commerce.connector.webhook.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adyen.commerce.connector.dto.BillingEventType;
import com.adyen.commerce.connector.dto.BillingSubscriptionRef;
import com.adyen.commerce.connector.dto.NormalizedBillingEvent;
import com.adyen.commerce.connector.dto.NormalizedSubscription;
import com.adyen.commerce.connector.dto.NormalizedSubscriptionStatus;
import com.adyen.commerce.connector.dto.RawWebhook;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.RetryableBillingException;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;
import com.adyen.commerce.connector.model.BillingWebhookEventApplicationModel;
import com.adyen.commerce.connector.model.BillingWebhookEventModel;
import com.adyen.commerce.connector.reconciliation.SubscriptionReconciliationService;
import com.adyen.commerce.connector.registry.SubscriptionBillingConnectorRegistry;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.search.SearchResult;

/** Verifies that webhooks trigger authoritative reconciliation rather than directly projecting status. */
@UnitTest
public class DefaultSubscriptionBillingWebhookDispatcherTest
{
	private static final Instant T1 = Instant.parse("2026-08-06T10:00:00Z");
	private static final Instant T2 = Instant.parse("2026-08-06T10:05:00Z");
	private static final Instant NOW = Instant.parse("2026-08-06T12:00:00Z");

	@Mock
	private SubscriptionBillingConnectorRegistry connectorRegistry;
	@Mock
	private FlexibleSearchService flexibleSearchService;
	@Mock
	private ModelService modelService;
	@Mock
	private SubscriptionBillingConnector connector;
	@Mock
	private SubscriptionReconciliationService reconciliationService;

	private DefaultSubscriptionBillingWebhookDispatcher dispatcher;
	private RawWebhook raw;
	private final Map<String, BillingWebhookEventModel> events = new HashMap<>();
	private final Map<String, BillingWebhookEventApplicationModel> applications = new HashMap<>();
	private final List<BillingSubscriptionRefModel> refs = new ArrayList<>();
	private final Map<String, AuthoritativeState> authoritative = new HashMap<>();

	@Before
	public void setUp() throws Exception
	{
		MockitoAnnotations.openMocks(this);
		dispatcher = new DefaultSubscriptionBillingWebhookDispatcher();
		dispatcher.setConnectorRegistry(connectorRegistry);
		dispatcher.setFlexibleSearchService(flexibleSearchService);
		dispatcher.setModelService(modelService);
		dispatcher.setReconciliationService(reconciliationService);
		dispatcher.setClock(Clock.fixed(NOW, ZoneOffset.UTC));

		raw = new RawWebhook(Map.of(), "{}", null);
		when(connectorRegistry.getConnector(BillingPlatform.RECURLY)).thenReturn(connector);
		when(modelService.create(BillingWebhookEventModel.class)).thenAnswer(i -> statefulEvent());
		when(modelService.create(BillingWebhookEventApplicationModel.class)).thenAnswer(i -> statefulApplication());
		doAnswer(i -> {
			final Object saved = i.getArgument(0);
			if (saved instanceof BillingWebhookEventModel event)
			{
				events.put(event.getEventId(), event);
			}
			else if (saved instanceof BillingWebhookEventApplicationModel application)
			{
				applications.put(applicationKey(application), application);
			}
			return null;
		}).when(modelService).save(any());
		when(flexibleSearchService.search(any(FlexibleSearchQuery.class)))
				.thenAnswer(i -> answerSearch(i.getArgument(0)));
		doAnswer(i -> reconcile((BillingSubscriptionRefModel) i.getArgument(0)))
				.when(reconciliationService).reconcile(any());
	}

	@Test
	public void createdFutureSubscriptionRemainsPending() throws Exception
	{
		final BillingSubscriptionRefModel ref = givenSubscription("sub-1", "PENDING");
		authoritative("sub-1", "PENDING", "monthly", 1);

		dispatch(eventOf(BillingEventType.SUBSCRIPTION_CREATED, "ev-1", "sub-1", T1));

		assertEquals("PENDING", ref.getStatus());
		assertEquals("RECONCILED", events.get("ev-1").getProcessingStatus());
		assertEquals("RECONCILED", application("ev-1", "sub-1").getProcessingStatus());
	}

	@Test
	public void paymentFailureDoesNotMakeAnActiveSubscriptionPastDue() throws Exception
	{
		final BillingSubscriptionRefModel ref = givenSubscription("sub-1", "ACTIVE");
		authoritative("sub-1", "ACTIVE", "monthly", 1);
		when(connector.resolveSubscriptionIds(any())).thenReturn(List.of("sub-1"));

		dispatch(eventOf(BillingEventType.PAYMENT_FAILED, "ev-1", null, T1));

		assertEquals("ACTIVE", ref.getStatus());
		verify(reconciliationService).reconcile(ref);
	}

	@Test
	public void expiredAndCanceledRemainDistinctAccordingToSnapshot() throws Exception
	{
		final BillingSubscriptionRefModel expired = givenSubscription("expired", "CANCELLED");
		final BillingSubscriptionRefModel canceled = givenSubscription("canceled", "ACTIVE");
		authoritative("expired", "EXPIRED", "monthly", 1);
		authoritative("canceled", "CANCELLED", "monthly", 1);

		dispatch(eventOf(BillingEventType.SUBSCRIPTION_EXPIRED, "ev-expired", "expired", T1));
		dispatch(eventOf(BillingEventType.SUBSCRIPTION_CANCELLED, "ev-canceled", "canceled", T1));

		assertEquals("EXPIRED", expired.getStatus());
		assertEquals("CANCELLED", canceled.getStatus());
	}

	@Test
	public void subscriptionUpdatedRefreshesPlanAndQuantity() throws Exception
	{
		final BillingSubscriptionRefModel ref = givenSubscription("sub-1", "ACTIVE");
		ref.setPlanCode("monthly");
		ref.setQuantity(1);
		authoritative("sub-1", "ACTIVE", "annual", 3);

		dispatch(eventOf(BillingEventType.SUBSCRIPTION_UPDATED, "ev-1", "sub-1", T1));

		assertEquals("annual", ref.getPlanCode());
		assertEquals(Integer.valueOf(3), ref.getQuantity());
		assertEquals(Long.valueOf(1L), ref.getEventVersion());
	}

	@Test
	public void outOfOrderEventsStillReadCurrentState() throws Exception
	{
		final BillingSubscriptionRefModel ref = givenSubscription("sub-1", "PENDING");
		authoritative("sub-1", "CANCELLED", "monthly", 1);
		dispatch(eventOf(BillingEventType.SUBSCRIPTION_CANCELLED, "ev-new", "sub-1", T2));

		authoritative("sub-1", "ACTIVE", "monthly", 1);
		dispatch(eventOf(BillingEventType.SUBSCRIPTION_RENEWED, "ev-old", "sub-1", T1));

		assertEquals("ACTIVE", ref.getStatus());
		verify(reconciliationService, times(2)).reconcile(ref);
		assertEquals("RECONCILED", events.get("ev-old").getProcessingStatus());
	}

	@Test
	public void eventsSharingTimestampAreBothReconciled() throws Exception
	{
		final BillingSubscriptionRefModel ref = givenSubscription("sub-1", "PENDING");
		authoritative("sub-1", "ACTIVE", "monthly", 1);

		dispatch(eventOf(BillingEventType.SUBSCRIPTION_CREATED, "ev-a", "sub-1", T1));
		dispatch(eventOf(BillingEventType.SUBSCRIPTION_UPDATED, "ev-b", "sub-1", T1));

		verify(reconciliationService, times(2)).reconcile(ref);
		assertEquals("ACTIVE", ref.getStatus());
	}

	@Test
	public void duplicateDeliveryDoesNotRepeatRemoteLookup() throws Exception
	{
		final BillingSubscriptionRefModel ref = givenSubscription("sub-1", "PENDING");
		authoritative("sub-1", "ACTIVE", "monthly", 1);
		final NormalizedBillingEvent event = eventOf(BillingEventType.SUBSCRIPTION_CREATED, "ev-1", "sub-1", T1);

		dispatch(event);
		dispatch(event);

		verify(reconciliationService, times(1)).reconcile(ref);
		assertEquals(Integer.valueOf(1), events.get("ev-1").getAttemptCount());
	}

	@Test
	public void directUnknownSubscriptionIsRetryableAndCanSucceedOnRedelivery() throws Exception
	{
		final NormalizedBillingEvent event = recurlySubscriptionEvent(BillingEventType.SUBSCRIPTION_CREATED,
				"ev-1", "sub-late", T1);

		assertThrows(RetryableBillingException.class, () -> dispatch(event));
		assertEquals("FAILED", events.get("ev-1").getProcessingStatus());
		assertEquals("RETRYABLE_UNKNOWN_SUBSCRIPTION",
				application("ev-1", "sub-late").getProcessingStatus());

		final BillingSubscriptionRefModel ref = givenSubscription("sub-late", "PENDING");
		authoritative("sub-late", "ACTIVE", "monthly", 1);
		dispatch(event);

		assertEquals("ACTIVE", ref.getStatus());
		assertEquals("RECONCILED", events.get("ev-1").getProcessingStatus());
		assertEquals(Integer.valueOf(2), application("ev-1", "sub-late").getAttemptCount());
	}

	@Test
	public void invoiceApplicationIsRecordedIndependentlyForEverySubscription() throws Exception
	{
		final BillingSubscriptionRefModel first = givenSubscription("sub-a", "ACTIVE");
		final BillingSubscriptionRefModel second = givenSubscription("sub-b", "ACTIVE");
		authoritative("sub-a", "PAST_DUE", "monthly", 1);
		authoritative("sub-b", "CANCELLED", "annual", 2);
		when(connector.resolveSubscriptionIds(any())).thenReturn(List.of("sub-a", "sub-b", "sub-a"));

		dispatch(eventOf(BillingEventType.INVOICE_PAST_DUE, "ev-1", null, T1));

		assertEquals("PAST_DUE", first.getStatus());
		assertEquals("CANCELLED", second.getStatus());
		assertEquals(2, applications.size());
		assertEquals("RECONCILED", application("ev-1", "sub-a").getProcessingStatus());
		assertEquals("RECONCILED", application("ev-1", "sub-b").getProcessingStatus());
	}

	@Test
	public void invoiceSubscriptionNotManagedLocallyIsTerminallySkipped() throws Exception
	{
		when(connector.resolveSubscriptionIds(any())).thenReturn(List.of("external-only"));

		dispatch(eventOf(BillingEventType.INVOICE_PAID, "ev-1", null, T1));

		assertEquals("SKIPPED_NO_SUBSCRIPTION", events.get("ev-1").getProcessingStatus());
		assertEquals("SKIPPED_UNKNOWN_SUBSCRIPTION",
				application("ev-1", "external-only").getProcessingStatus());
	}

	@Test
	public void unknownEventWithNoSubscriptionIsExplicitlyUnsupported() throws Exception
	{
		when(connector.resolveSubscriptionIds(any())).thenReturn(List.of());

		dispatch(eventOf(BillingEventType.UNKNOWN, "ev-1", null, T1));

		assertEquals("SKIPPED_UNSUPPORTED", events.get("ev-1").getProcessingStatus());
	}

	@Test
	public void eventVersionChangesOnlyWhenTheAuthoritativeProjectionChanges() throws Exception
	{
		final BillingSubscriptionRefModel ref = givenSubscription("sub-1", "ACTIVE");
		ref.setPlanCode("monthly");
		ref.setQuantity(1);
		ref.setCurrentPeriodStart(Date.from(T1));
		ref.setCurrentPeriodEnd(Date.from(T2));
		ref.setCancelAtPeriodEnd(false);
		ref.setPlatformUpdatedAt(Date.from(T2));
		authoritative("sub-1", "ACTIVE", "monthly", 1);

		dispatch(eventOf(BillingEventType.SUBSCRIPTION_UPDATED, "ev-1", "sub-1", T1));
		assertNull(ref.getEventVersion());

		authoritative("sub-1", "ACTIVE", "annual", 1);
		dispatch(eventOf(BillingEventType.SUBSCRIPTION_UPDATED, "ev-2", "sub-1", T2));
		assertEquals(Long.valueOf(1L), ref.getEventVersion());
	}

	@Test
	public void retryableReconciliationFailureIsEligibleForRedelivery() throws Exception
	{
		final BillingSubscriptionRefModel ref = givenSubscription("sub-1", "PENDING");
		final NormalizedBillingEvent event = eventOf(BillingEventType.SUBSCRIPTION_CREATED, "ev-1", "sub-1", T1);
		doThrow(new RetryableBillingException("upstream down"))
				.doAnswer(i -> reconcile((BillingSubscriptionRefModel) i.getArgument(0)))
				.when(reconciliationService).reconcile(ref);
		authoritative("sub-1", "ACTIVE", "monthly", 1);

		assertThrows(RetryableBillingException.class, () -> dispatch(event));
		dispatch(event);

		assertEquals("ACTIVE", ref.getStatus());
		assertEquals(Integer.valueOf(2), events.get("ev-1").getAttemptCount());
		assertEquals("RECONCILED", application("ev-1", "sub-1").getProcessingStatus());
	}

	@Test
	public void eventWithoutPlatformIdIsDeduplicatedByPayload() throws Exception
	{
		final BillingSubscriptionRefModel ref = givenSubscription("sub-1", "PENDING");
		authoritative("sub-1", "ACTIVE", "monthly", 1);
		final RawWebhook body = new RawWebhook(Map.of(), "{\"same\":true}", null);
		final NormalizedBillingEvent event = eventOf(BillingEventType.SUBSCRIPTION_CREATED, null, "sub-1", T1);
		when(connector.parseWebhook(body)).thenReturn(event);

		dispatcher.dispatch(BillingPlatform.RECURLY, body);
		dispatcher.dispatch(BillingPlatform.RECURLY, body);

		verify(reconciliationService, times(1)).reconcile(ref);
		assertTrue(events.keySet().iterator().next().startsWith("derived:"));
	}

	@Test
	public void dispatchStillReturnsTheNormalizedEvent() throws Exception
	{
		givenSubscription("sub-1", "ACTIVE");
		authoritative("sub-1", "ACTIVE", "monthly", 1);
		final NormalizedBillingEvent event = eventOf(BillingEventType.SUBSCRIPTION_UPDATED, "ev-1", "sub-1", T1);
		when(connector.parseWebhook(raw)).thenReturn(event);

		assertSame(event, dispatcher.dispatch(BillingPlatform.RECURLY, raw));
	}

	@Test
	public void nullEventIsAcknowledgedWithoutPersistence() throws Exception
	{
		when(connector.parseWebhook(raw)).thenReturn(null);

		assertNull(dispatcher.dispatch(BillingPlatform.RECURLY, raw));
		verify(modelService, never()).save(any());
	}

	private void dispatch(final NormalizedBillingEvent event) throws Exception
	{
		when(connector.parseWebhook(raw)).thenReturn(event);
		dispatcher.dispatch(BillingPlatform.RECURLY, raw);
	}

	private BillingSubscriptionRefModel givenSubscription(final String id, final String status)
	{
		final BillingSubscriptionRefModel ref = statefulRef(id);
		ref.setStatus(status);
		refs.add(ref);
		return ref;
	}

	private void authoritative(final String id, final String status, final String plan, final int quantity)
	{
		authoritative.put(id, new AuthoritativeState(status, plan, quantity));
	}

	private NormalizedSubscription reconcile(final BillingSubscriptionRefModel ref)
	{
		final AuthoritativeState state = authoritative.get(ref.getExternalSubscriptionId());
		ref.setStatus(state.status());
		ref.setPlanCode(state.plan());
		ref.setQuantity(state.quantity());
		ref.setCurrentPeriodStart(Date.from(T1));
		ref.setCurrentPeriodEnd(Date.from(T2));
		ref.setCancelAtPeriodEnd("CANCELLED".equals(state.status()));
		ref.setPlatformUpdatedAt(Date.from(T2));
		ref.setLastSyncedAt(Date.from(NOW));
		return new NormalizedSubscription(
				new BillingSubscriptionRef(BillingPlatform.RECURLY, ref.getExternalSubscriptionId()),
				NormalizedSubscriptionStatus.valueOf(state.status()), state.plan(), state.quantity(), T1, T2,
				"CANCELLED".equals(state.status()), T2);
	}

	private BillingWebhookEventApplicationModel application(final String eventId, final String subscriptionId)
	{
		return applications.get(eventId + "/" + subscriptionId);
	}

	private SearchResult<?> answerSearch(final FlexibleSearchQuery query)
	{
		final List<Object> rows = new ArrayList<>();
		if (query.getQuery().contains("{BillingWebhookEventApplication}"))
		{
			final BillingWebhookEventModel event = (BillingWebhookEventModel) query.getQueryParameters().get("event");
			final Object id = query.getQueryParameters().get("externalSubscriptionId");
			final BillingWebhookEventApplicationModel found = applications.get(event.getEventId() + "/" + id);
			if (found != null)
			{
				rows.add(found);
			}
		}
		else if (query.getQuery().contains("{BillingWebhookEvent}"))
		{
			final BillingWebhookEventModel found = events.get(query.getQueryParameters().get("eventId"));
			if (found != null)
			{
				rows.add(found);
			}
		}
		else
		{
			final Object id = query.getQueryParameters().get("externalSubscriptionId");
			refs.stream().filter(ref -> id.equals(ref.getExternalSubscriptionId())).forEach(rows::add);
		}
		@SuppressWarnings("unchecked")
		final SearchResult<Object> result = mock(SearchResult.class);
		when(result.getResult()).thenReturn(rows);
		return result;
	}

	private static NormalizedBillingEvent eventOf(final BillingEventType type, final String eventId,
			final String subscriptionId, final Instant occurredAt)
	{
		return new NormalizedBillingEvent(BillingPlatform.RECURLY, type, eventId, subscriptionId, "cust-1",
				occurredAt, Map.of());
	}

	private static NormalizedBillingEvent recurlySubscriptionEvent(final BillingEventType type, final String eventId,
			final String subscriptionId, final Instant occurredAt)
	{
		return new NormalizedBillingEvent(BillingPlatform.RECURLY, type, eventId, subscriptionId, "cust-1",
				occurredAt, Map.of("objectType", "subscription"));
	}

	private static BillingSubscriptionRefModel statefulRef(final String id)
	{
		final Map<String, Object> state = new HashMap<>();
		final BillingSubscriptionRefModel ref = mock(BillingSubscriptionRefModel.class);
		when(ref.getExternalSubscriptionId()).thenReturn(id);
		doAnswer(i -> { state.put("status", i.getArgument(0)); return null; }).when(ref).setStatus(any());
		when(ref.getStatus()).thenAnswer(i -> state.get("status"));
		doAnswer(i -> { state.put("plan", i.getArgument(0)); return null; }).when(ref).setPlanCode(any());
		when(ref.getPlanCode()).thenAnswer(i -> state.get("plan"));
		doAnswer(i -> { state.put("quantity", i.getArgument(0)); return null; }).when(ref).setQuantity(any());
		when(ref.getQuantity()).thenAnswer(i -> state.get("quantity"));
		doAnswer(i -> { state.put("periodStart", i.getArgument(0)); return null; }).when(ref).setCurrentPeriodStart(any());
		when(ref.getCurrentPeriodStart()).thenAnswer(i -> state.get("periodStart"));
		doAnswer(i -> { state.put("periodEnd", i.getArgument(0)); return null; }).when(ref).setCurrentPeriodEnd(any());
		when(ref.getCurrentPeriodEnd()).thenAnswer(i -> state.get("periodEnd"));
		doAnswer(i -> { state.put("cancelAtEnd", i.getArgument(0)); return null; }).when(ref).setCancelAtPeriodEnd(any());
		when(ref.getCancelAtPeriodEnd()).thenAnswer(i -> state.get("cancelAtEnd"));
		doAnswer(i -> { state.put("platformUpdated", i.getArgument(0)); return null; }).when(ref).setPlatformUpdatedAt(any());
		when(ref.getPlatformUpdatedAt()).thenAnswer(i -> state.get("platformUpdated"));
		doAnswer(i -> { state.put("lastSynced", i.getArgument(0)); return null; }).when(ref).setLastSyncedAt(any());
		when(ref.getLastSyncedAt()).thenAnswer(i -> state.get("lastSynced"));
		doAnswer(i -> { state.put("eventId", i.getArgument(0)); return null; }).when(ref).setLastAppliedEventId(any());
		when(ref.getLastAppliedEventId()).thenAnswer(i -> state.get("eventId"));
		doAnswer(i -> { state.put("eventAt", i.getArgument(0)); return null; }).when(ref).setLastAppliedEventAt(any());
		when(ref.getLastAppliedEventAt()).thenAnswer(i -> state.get("eventAt"));
		doAnswer(i -> { state.put("version", i.getArgument(0)); return null; }).when(ref).setEventVersion(any());
		when(ref.getEventVersion()).thenAnswer(i -> state.get("version"));
		return ref;
	}

	private static BillingWebhookEventModel statefulEvent()
	{
		final Map<String, Object> state = new HashMap<>();
		final BillingWebhookEventModel event = mock(BillingWebhookEventModel.class);
		doAnswer(i -> { state.put("eventId", i.getArgument(0)); return null; }).when(event).setEventId(any());
		when(event.getEventId()).thenAnswer(i -> state.get("eventId"));
		doAnswer(i -> { state.put("status", i.getArgument(0)); return null; }).when(event).setProcessingStatus(any());
		when(event.getProcessingStatus()).thenAnswer(i -> state.get("status"));
		doAnswer(i -> { state.put("attempts", i.getArgument(0)); return null; }).when(event).setAttemptCount(any());
		when(event.getAttemptCount()).thenAnswer(i -> state.get("attempts"));
		doAnswer(i -> { state.put("lastError", i.getArgument(0)); return null; }).when(event).setLastError(any());
		when(event.getLastError()).thenAnswer(i -> state.get("lastError"));
		doAnswer(i -> { state.put("subscription", i.getArgument(0)); return null; }).when(event).setSubscriptionRef(any());
		when(event.getSubscriptionRef()).thenAnswer(i -> state.get("subscription"));
		return event;
	}

	private static BillingWebhookEventApplicationModel statefulApplication()
	{
		final Map<String, Object> state = new HashMap<>();
		final BillingWebhookEventApplicationModel application = mock(BillingWebhookEventApplicationModel.class);
		doAnswer(i -> { state.put("event", i.getArgument(0)); return null; }).when(application).setEvent(any());
		when(application.getEvent()).thenAnswer(i -> state.get("event"));
		doAnswer(i -> { state.put("subscriptionId", i.getArgument(0)); return null; })
				.when(application).setExternalSubscriptionId(any());
		when(application.getExternalSubscriptionId()).thenAnswer(i -> state.get("subscriptionId"));
		doAnswer(i -> { state.put("status", i.getArgument(0)); return null; }).when(application).setProcessingStatus(any());
		when(application.getProcessingStatus()).thenAnswer(i -> state.get("status"));
		doAnswer(i -> { state.put("attempts", i.getArgument(0)); return null; }).when(application).setAttemptCount(any());
		when(application.getAttemptCount()).thenAnswer(i -> state.get("attempts"));
		doAnswer(i -> { state.put("lastError", i.getArgument(0)); return null; }).when(application).setLastError(any());
		when(application.getLastError()).thenAnswer(i -> state.get("lastError"));
		doAnswer(i -> { state.put("subscription", i.getArgument(0)); return null; }).when(application).setSubscriptionRef(any());
		when(application.getSubscriptionRef()).thenAnswer(i -> state.get("subscription"));
		doAnswer(i -> { state.put("reconciledAt", i.getArgument(0)); return null; }).when(application).setReconciledAt(any());
		when(application.getReconciledAt()).thenAnswer(i -> state.get("reconciledAt"));
		return application;
	}

	private static String applicationKey(final BillingWebhookEventApplicationModel application)
	{
		return application.getEvent().getEventId() + "/" + application.getExternalSubscriptionId();
	}

	private record AuthoritativeState(String status, String plan, int quantity)
	{
	}
}
