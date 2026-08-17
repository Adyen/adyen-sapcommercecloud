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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
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
import com.adyen.commerce.connector.dto.NormalizedBillingEvent;
import com.adyen.commerce.connector.dto.RawWebhook;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;
import com.adyen.commerce.connector.model.BillingWebhookEventModel;
import com.adyen.commerce.connector.registry.SubscriptionBillingConnectorRegistry;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.search.SearchResult;

/**
 * Unit test for {@link DefaultSubscriptionBillingWebhookDispatcher}: dispatch delegates to the
 * platform-resolved connector's {@code parseWebhook}, then applies the event under the dedup and ordering rules —
 * dedup on the platform event id, discard events older than the last applied one, and project platform
 * state onto the local reference.
 *
 * <p>The models are stateful mocks rather than plain mocks, and {@code ModelService}/
 * {@code FlexibleSearchService} are backed by an in-memory store, because the two headline claims —
 * "a replay changes nothing" and "out-of-order converges" — are only meaningful if a reference actually
 * carries its watermark from one dispatch into the next. A plain mock returning null from every getter
 * would pass those tests without the rules doing any work. Generated hybris models route their setters
 * through a persistence context, so they cannot simply be instantiated here.
 */
@UnitTest
public class DefaultSubscriptionBillingWebhookDispatcherTest
{
	private static final Instant T1 = Instant.parse("2026-08-06T10:00:00Z");
	private static final Instant T2 = Instant.parse("2026-08-06T10:05:00Z");
	private static final Instant T3 = Instant.parse("2026-08-06T10:10:00Z");
	private static final Instant NOW = Instant.parse("2026-08-06T12:00:00Z");

	@Mock
	private SubscriptionBillingConnectorRegistry connectorRegistry;
	@Mock
	private FlexibleSearchService flexibleSearchService;
	@Mock
	private ModelService modelService;
	@Mock
	private SubscriptionBillingConnector connector;

	private DefaultSubscriptionBillingWebhookDispatcher dispatcher;
	private RawWebhook raw;

	/** Persisted BillingWebhookEvent rows, keyed by event id — stands in for the unique index. */
	private final Map<String, BillingWebhookEventModel> eventStore = new HashMap<>();
	/** What findByExternalId will return. */
	private final List<BillingSubscriptionRefModel> refs = new ArrayList<>();

	@Before
	public void setUp() throws Exception
	{
		MockitoAnnotations.openMocks(this);
		dispatcher = new DefaultSubscriptionBillingWebhookDispatcher();
		dispatcher.setConnectorRegistry(connectorRegistry);
		dispatcher.setFlexibleSearchService(flexibleSearchService);
		dispatcher.setModelService(modelService);
		dispatcher.setClock(Clock.fixed(NOW, ZoneOffset.UTC));

		raw = new RawWebhook(Map.of(), "{}", null);
		when(connectorRegistry.getConnector(BillingPlatform.CHARGEBEE)).thenReturn(connector);
		when(modelService.create(BillingWebhookEventModel.class)).thenAnswer(i -> statefulEvent());
		doAnswer(i -> {
			final Object saved = i.getArgument(0);
			if (saved instanceof BillingWebhookEventModel)
			{
				final BillingWebhookEventModel record = (BillingWebhookEventModel) saved;
				eventStore.put(record.getEventId(), record);
			}
			return null;
		}).when(modelService).save(any());
		when(flexibleSearchService.search(any(FlexibleSearchQuery.class))).thenAnswer(i -> answerSearch(i.getArgument(0)));
	}

	// ---------------------------------------------------------------- duplicate rule

	@Test
	public void replayingTheSameWebhookLeavesStateUnchangedAfterTheFirstApplication() throws Exception
	{
		final BillingSubscriptionRefModel ref = givenSubscription("sub-1");
		final NormalizedBillingEvent event = eventOf(BillingEventType.SUBSCRIPTION_ACTIVATED, "ev-1", "sub-1", T1);
		when(connector.parseWebhook(raw)).thenReturn(event);

		dispatcher.dispatch(BillingPlatform.CHARGEBEE, raw);
		final String statusAfterFirst = ref.getStatus();
		final Long versionAfterFirst = ref.getEventVersion();
		final Date syncedAfterFirst = ref.getLastSyncedAt();

		dispatcher.dispatch(BillingPlatform.CHARGEBEE, raw);

		assertEquals("ACTIVE", statusAfterFirst);
		assertEquals(Long.valueOf(1L), versionAfterFirst);
		assertEquals(statusAfterFirst, ref.getStatus());
		assertEquals(versionAfterFirst, ref.getEventVersion());
		assertEquals(syncedAfterFirst, ref.getLastSyncedAt());
		// Applied exactly once — not "applied twice with the same value", which would pass on state alone.
		verify(ref, times(1)).setStatus("ACTIVE");
		verify(ref, times(1)).setEventVersion(any());
		assertEquals(1, eventStore.size());
	}

	@Test
	public void duplicateIsSuppressedEvenWhenTheFirstDeliveryChangedNothing() throws Exception
	{
		// No local reference: the first delivery applies nothing, but it is still a finished verdict.
		when(connector.parseWebhook(raw))
				.thenReturn(eventOf(BillingEventType.SUBSCRIPTION_ACTIVATED, "ev-1", "sub-unknown", T1));

		dispatcher.dispatch(BillingPlatform.CHARGEBEE, raw);
		dispatcher.dispatch(BillingPlatform.CHARGEBEE, raw);

		final BillingWebhookEventModel record = eventStore.get("ev-1");
		assertEquals("SKIPPED_UNKNOWN_SUBSCRIPTION", record.getProcessingStatus());
		assertEquals(Integer.valueOf(1), record.getAttemptCount());
		verify(modelService, times(1)).create(BillingWebhookEventModel.class);
	}

	@Test
	public void eventWithoutAPlatformEventIdIsStillDedupedOnADerivedKey() throws Exception
	{
		final BillingSubscriptionRefModel ref = givenSubscription("sub-1");
		when(connector.parseWebhook(raw))
				.thenReturn(eventOf(BillingEventType.SUBSCRIPTION_ACTIVATED, null, "sub-1", T1));

		dispatcher.dispatch(BillingPlatform.CHARGEBEE, raw);
		dispatcher.dispatch(BillingPlatform.CHARGEBEE, raw);

		verify(ref, times(1)).setStatus("ACTIVE");
		assertEquals(1, eventStore.size());
		assertTrue(eventStore.keySet().iterator().next().startsWith("derived:"));
	}

	@Test
	public void concurrentDeliveryThatLosesTheClaimDoesNotApplyTheEvent() throws Exception
	{
		final BillingSubscriptionRefModel ref = givenSubscription("sub-1");
		when(connector.parseWebhook(raw))
				.thenReturn(eventOf(BillingEventType.SUBSCRIPTION_ACTIVATED, "ev-1", "sub-1", T1));
		// Stands in for the unique index rejecting the second concurrent insert of the same event id: our
		// save loses, and what is visible afterwards is the winning delivery's row.
		doAnswer(i -> {
			eventStore.put(((BillingWebhookEventModel) i.getArgument(0)).getEventId(), i.getArgument(0));
			throw new IllegalStateException("unique index violation");
		}).when(modelService).save(any());

		dispatcher.dispatch(BillingPlatform.CHARGEBEE, raw);

		verify(ref, never()).setStatus(any());
		verify(ref, never()).setEventVersion(any());
	}

	// ---------------------------------------------------------------- stale rule

	@Test
	public void staleEventIsDiscardedRatherThanApplied() throws Exception
	{
		final BillingSubscriptionRefModel ref = givenSubscription("sub-1");
		dispatch(eventOf(BillingEventType.SUBSCRIPTION_CANCELLED, "ev-late", "sub-1", T2));
		dispatch(eventOf(BillingEventType.SUBSCRIPTION_RENEWED, "ev-early", "sub-1", T1));

		assertEquals("CANCELLED", ref.getStatus());
		assertEquals(Long.valueOf(1L), ref.getEventVersion());
		assertEquals("SKIPPED_STALE", eventStore.get("ev-early").getProcessingStatus());
		verify(ref, never()).setStatus("ACTIVE");
	}

	@Test
	public void outOfOrderDeliveryConvergesToTheSameFinalStateAsInOrder() throws Exception
	{
		final NormalizedBillingEvent renewed = eventOf(BillingEventType.SUBSCRIPTION_RENEWED, "ev-a", "sub-1", T1);
		final NormalizedBillingEvent cancelled = eventOf(BillingEventType.SUBSCRIPTION_CANCELLED, "ev-b", "sub-1", T2);

		final BillingSubscriptionRefModel inOrder = givenSubscription("sub-1");
		dispatch(renewed);
		dispatch(cancelled);
		final String inOrderStatus = inOrder.getStatus();
		final Long inOrderVersion = inOrder.getEventVersion();

		resetStore();
		final BillingSubscriptionRefModel reversed = givenSubscription("sub-1");
		dispatch(cancelled);
		dispatch(renewed);

		assertEquals("CANCELLED", inOrderStatus);
		assertEquals(inOrderStatus, reversed.getStatus());
		// Same end state, and the late-arriving old event was discarded rather than applied.
		assertEquals(Long.valueOf(2L), inOrderVersion);
		assertEquals(Long.valueOf(1L), reversed.getEventVersion());
	}

	@Test
	public void eventsSharingATimestampAreNotGuessedAtAndFlagTheReferenceForRefetch() throws Exception
	{
		final BillingSubscriptionRefModel ref = givenSubscription("sub-1");
		dispatch(eventOf(BillingEventType.SUBSCRIPTION_ACTIVATED, "ev-a", "sub-1", T1));
		assertNotNull("first event should have marked the projection confirmed", ref.getLastSyncedAt());

		dispatch(eventOf(BillingEventType.INVOICE_PAYMENT_FAILED, "ev-b", "sub-1", T1));

		assertEquals("ACTIVE", ref.getStatus());
		assertEquals(Long.valueOf(1L), ref.getEventVersion());
		assertNull("an undecidable order must leave the projection unconfirmed for the re-fetch sweep",
				ref.getLastSyncedAt());
		assertEquals("SKIPPED_AMBIGUOUS", eventStore.get("ev-b").getProcessingStatus());
	}

	// ---------------------------------------------------------------- subscription resolution

	@Test
	public void eventThatNamesNoSubscriptionIsResolvedThroughTheConnector() throws Exception
	{
		final BillingSubscriptionRefModel ref = givenSubscription("sub-9");
		when(connector.resolveSubscriptionIds(any())).thenReturn(List.of("sub-9"));

		dispatch(eventOf(BillingEventType.INVOICE_PAID, "ev-1", null, T1));

		assertEquals("ACTIVE", ref.getStatus());
		assertEquals("APPLIED", eventStore.get("ev-1").getProcessingStatus());
	}

	@Test
	public void resolutionIsNotRepeatedOnARedeliveryBecauseTheEventIdIsClaimedFirst() throws Exception
	{
		givenSubscription("sub-9");
		when(connector.resolveSubscriptionIds(any())).thenReturn(List.of("sub-9"));
		final NormalizedBillingEvent event = eventOf(BillingEventType.INVOICE_PAID, "ev-1", null, T1);

		dispatch(event);
		dispatch(event);

		// Resolution is a live call to the platform; a redelivery must not pay for it a second time.
		verify(connector, times(1)).resolveSubscriptionIds(any());
	}

	@Test
	public void oneEventAppliesToEverySubscriptionItResolvesTo() throws Exception
	{
		// An invoice can cover more than one subscription, and the event applies to all of them.
		final BillingSubscriptionRefModel first = givenSubscription("sub-a");
		final BillingSubscriptionRefModel second = givenSubscription("sub-b");
		when(connector.resolveSubscriptionIds(any())).thenReturn(List.of("sub-a", "sub-b"));

		dispatch(eventOf(BillingEventType.INVOICE_PAYMENT_FAILED, "ev-1", null, T1));

		assertEquals("PAST_DUE", first.getStatus());
		assertEquals("PAST_DUE", second.getStatus());
		assertEquals("APPLIED", eventStore.get("ev-1").getProcessingStatus());
	}

	@Test
	public void eventThatResolvesToNothingIsRecordedRatherThanRetriedForever() throws Exception
	{
		when(connector.resolveSubscriptionIds(any())).thenReturn(List.of());

		dispatch(eventOf(BillingEventType.INVOICE_PAID, "ev-1", null, T1));

		assertEquals("SKIPPED_UNKNOWN_SUBSCRIPTION", eventStore.get("ev-1").getProcessingStatus());
	}

	@Test
	public void connectorIsNotAskedToResolveAnEventThatAlreadyNamesItsSubscription() throws Exception
	{
		givenSubscription("sub-1");

		dispatch(eventOf(BillingEventType.SUBSCRIPTION_ACTIVATED, "ev-1", "sub-1", T1));

		verify(connector, never()).resolveSubscriptionIds(any());
	}

	@Test
	public void theDedupKeyIsClaimedBeforeTheConnectorIsAskedToResolve() throws Exception
	{
		givenSubscription("sub-9");
		when(connector.resolveSubscriptionIds(any())).thenAnswer(i -> {
			// The whole point of the ordering: by the time the remote lookup runs, the row that suppresses
			// a redelivery must already be persisted.
			assertNotNull("event id must already be claimed", eventStore.get("ev-1"));
			return List.of("sub-9");
		});

		dispatch(eventOf(BillingEventType.INVOICE_PAID, "ev-1", null, T1));

		verify(connector).resolveSubscriptionIds(any());
	}

	@Test
	public void aPartlyAppliedMultiSubscriptionEventIsRecordedAsApplied() throws Exception
	{
		final BillingSubscriptionRefModel fresh = givenSubscription("sub-a");
		final BillingSubscriptionRefModel ahead = givenSubscription("sub-b");
		// sub-b has already seen something newer, so the event is stale for it but not for sub-a.
		seedNewerEventFor("sub-b");
		when(connector.resolveSubscriptionIds(any())).thenReturn(List.of("sub-a", "sub-b"));

		dispatch(eventOf(BillingEventType.INVOICE_PAYMENT_FAILED, "ev-2", null, T1));

		assertEquals("PAST_DUE", fresh.getStatus());
		assertEquals("ACTIVE", ahead.getStatus());
		// One row, one status: applied somewhere outranks skipped somewhere else.
		assertEquals("APPLIED", eventStore.get("ev-2").getProcessingStatus());
	}

	@Test
	public void anEventThatIsStaleForEverySubscriptionIsRecordedAsStale() throws Exception
	{
		final BillingSubscriptionRefModel ahead = givenSubscription("sub-b");
		seedNewerEventFor("sub-b");
		when(connector.resolveSubscriptionIds(any())).thenReturn(List.of("sub-b"));

		dispatch(eventOf(BillingEventType.INVOICE_PAYMENT_FAILED, "ev-2", null, T1));

		assertEquals("ACTIVE", ahead.getStatus());
		assertEquals("SKIPPED_STALE", eventStore.get("ev-2").getProcessingStatus());
	}

	@Test
	public void twoDeliveriesWithNoEventIdAreToldApartByTheirPayload() throws Exception
	{
		final BillingSubscriptionRefModel first = givenSubscription("sub-a");
		final BillingSubscriptionRefModel second = givenSubscription("sub-b");
		when(connector.resolveSubscriptionIds(any())).thenReturn(List.of("sub-a"), List.of("sub-b"));

		// Same type, same second, no platform event id, different bodies: two real events, not a replay.
		dispatchRaw(eventOf(BillingEventType.INVOICE_PAID, null, null, T1), "{\"invoice\":\"one\"}");
		dispatchRaw(eventOf(BillingEventType.INVOICE_PAID, null, null, T1), "{\"invoice\":\"two\"}");

		assertEquals("ACTIVE", first.getStatus());
		assertEquals("ACTIVE", second.getStatus());
		assertEquals(2, eventStore.size());
	}

	@Test
	public void anIdenticalRedeliveryWithNoEventIdIsStillSuppressed() throws Exception
	{
		final BillingSubscriptionRefModel ref = givenSubscription("sub-a");
		when(connector.resolveSubscriptionIds(any())).thenReturn(List.of("sub-a"));

		dispatchRaw(eventOf(BillingEventType.INVOICE_PAID, null, null, T1), "{\"invoice\":\"one\"}");
		dispatchRaw(eventOf(BillingEventType.INVOICE_PAID, null, null, T1), "{\"invoice\":\"one\"}");

		verify(ref, times(1)).setStatus("ACTIVE");
		assertEquals(1, eventStore.size());
	}

	@Test
	public void aSaveFailureThatIsNotADuplicateFailsLoudlySoThePlatformRedelivers() throws Exception
	{
		givenSubscription("sub-1");
		when(connector.parseWebhook(raw))
				.thenReturn(eventOf(BillingEventType.SUBSCRIPTION_ACTIVATED, "ev-1", "sub-1", T1));
		doThrow(new IllegalStateException("lock timeout")).when(modelService).save(any());

		// Swallowing this would ACK the webhook, so the platform would never send it again and no row
		// would exist to show anything was lost.
		assertThrows(IllegalStateException.class, () -> dispatcher.dispatch(BillingPlatform.CHARGEBEE, raw));
	}

	// ---------------------------------------------------------------- projection

	@Test
	public void appliedEventAdvancesTheWatermark() throws Exception
	{
		final BillingSubscriptionRefModel ref = givenSubscription("sub-1");
		dispatch(eventOf(BillingEventType.SUBSCRIPTION_ACTIVATED, "ev-1", "sub-1", T1));

		assertEquals("ACTIVE", ref.getStatus());
		assertEquals("ev-1", ref.getLastAppliedEventId());
		assertEquals(Date.from(T1), ref.getLastAppliedEventAt());
		assertEquals(Date.from(NOW), ref.getLastSyncedAt());
		assertEquals(Long.valueOf(1L), ref.getEventVersion());
		assertEquals("APPLIED", eventStore.get("ev-1").getProcessingStatus());
	}

	@Test
	public void unmappedEventTypeLeavesTheReferenceCompletelyAlone() throws Exception
	{
		final BillingSubscriptionRefModel ref = givenSubscription("sub-1");
		dispatch(eventOf(BillingEventType.PAYMENT_METHOD_UPDATED, "ev-1", "sub-1", T1));

		verify(ref, never()).setStatus(any());
		// The watermark tracks status claims, and this event makes none, so it must not move it.
		assertNull(ref.getLastAppliedEventAt());
		assertNull(ref.getEventVersion());
		assertEquals("APPLIED", eventStore.get("ev-1").getProcessingStatus());
	}

	@Test
	public void statuslessEventDoesNotMakeARealStatusEventInTheSameSecondLookAmbiguous() throws Exception
	{
		final BillingSubscriptionRefModel ref = givenSubscription("sub-1");

		// Platforms with whole-second timestamps routinely emit both at once — Recurly sends
		// subscription/updated alongside subscription/canceled. If the status-less one moved the watermark,
		// the cancellation would look like an unresolvable tie and be dropped for good.
		dispatch(eventOf(BillingEventType.PAYMENT_METHOD_UPDATED, "ev-a", "sub-1", T1));
		dispatch(eventOf(BillingEventType.SUBSCRIPTION_CANCELLED, "ev-b", "sub-1", T1));

		assertEquals("CANCELLED", ref.getStatus());
		assertEquals("APPLIED", eventStore.get("ev-b").getProcessingStatus());
	}

	@Test
	public void statuslessEventDoesNotMakeALaterStatusChangeLookStale() throws Exception
	{
		final BillingSubscriptionRefModel ref = givenSubscription("sub-1");
		dispatch(eventOf(BillingEventType.SUBSCRIPTION_ACTIVATED, "ev-a", "sub-1", T1));
		dispatch(eventOf(BillingEventType.PAYMENT_METHOD_UPDATED, "ev-b", "sub-1", T3));

		// T2 is older than the payment-method change but newer than the last real status claim.
		dispatch(eventOf(BillingEventType.SUBSCRIPTION_CANCELLED, "ev-c", "sub-1", T2));

		assertEquals("CANCELLED", ref.getStatus());
	}

	@Test
	public void dispatchUpdatesStatusToActiveOnSubscriptionActivated() throws Exception
	{
		assertProjectedStatus(BillingEventType.SUBSCRIPTION_ACTIVATED, "ACTIVE");
	}

	@Test
	public void dispatchUpdatesStatusToActiveOnSubscriptionRenewed() throws Exception
	{
		assertProjectedStatus(BillingEventType.SUBSCRIPTION_RENEWED, "ACTIVE");
	}

	@Test
	public void dispatchUpdatesStatusToActiveOnSubscriptionResumed() throws Exception
	{
		assertProjectedStatus(BillingEventType.SUBSCRIPTION_RESUMED, "ACTIVE");
	}

	@Test
	public void dispatchUpdatesStatusToActiveOnInvoicePaid() throws Exception
	{
		assertProjectedStatus(BillingEventType.INVOICE_PAID, "ACTIVE");
	}

	@Test
	public void dispatchUpdatesStatusToCancelledOnSubscriptionCancelled() throws Exception
	{
		assertProjectedStatus(BillingEventType.SUBSCRIPTION_CANCELLED, "CANCELLED");
	}

	@Test
	public void dispatchUpdatesStatusToPausedOnSubscriptionPaused() throws Exception
	{
		assertProjectedStatus(BillingEventType.SUBSCRIPTION_PAUSED, "PAUSED");
	}

	@Test
	public void dispatchUpdatesStatusToPastDueOnInvoicePaymentFailed() throws Exception
	{
		assertProjectedStatus(BillingEventType.INVOICE_PAYMENT_FAILED, "PAST_DUE");
	}

	// ---------------------------------------------------------------- dispatch plumbing

	@Test
	public void dispatchDelegatesToPlatformResolvedConnector() throws Exception
	{
		final NormalizedBillingEvent event = eventOf(BillingEventType.SUBSCRIPTION_ACTIVATED, "ev-1", "sub-1", T1);
		when(connector.parseWebhook(raw)).thenReturn(event);

		final NormalizedBillingEvent result = dispatcher.dispatch(BillingPlatform.CHARGEBEE, raw);

		assertSame(event, result);
		verify(connectorRegistry).getConnector(BillingPlatform.CHARGEBEE);
	}

	@Test
	public void dispatchReturnsNullWhenConnectorHasNoInterestingEvent() throws Exception
	{
		when(connector.parseWebhook(raw)).thenReturn(null);

		final NormalizedBillingEvent result = dispatcher.dispatch(BillingPlatform.CHARGEBEE, raw);

		assertNull(result);
		verify(modelService, never()).save(any());
	}

	@Test
	public void dispatchIgnoresEventForUnknownSubscription() throws Exception
	{
		when(connector.parseWebhook(raw))
				.thenReturn(eventOf(BillingEventType.SUBSCRIPTION_ACTIVATED, "ev-1", "sub-unknown", T1));

		dispatcher.dispatch(BillingPlatform.CHARGEBEE, raw);

		assertEquals("SKIPPED_UNKNOWN_SUBSCRIPTION", eventStore.get("ev-1").getProcessingStatus());
	}

	@Test
	public void failedApplicationStaysEligibleForRedelivery() throws Exception
	{
		final BillingSubscriptionRefModel ref = givenSubscription("sub-1");
		when(connector.parseWebhook(raw))
				.thenReturn(eventOf(BillingEventType.SUBSCRIPTION_ACTIVATED, "ev-1", "sub-1", T1));
		doThrow(new IllegalStateException("boom")).when(modelService).save(ref);

		try
		{
			dispatcher.dispatch(BillingPlatform.CHARGEBEE, raw);
			fail("expected the failure to propagate so the platform retries");
		}
		catch (final IllegalStateException expected)
		{
			// expected
		}

		final BillingWebhookEventModel record = eventStore.get("ev-1");
		assertEquals("FAILED", record.getProcessingStatus());
		assertTrue(record.getLastError().contains("boom"));

		// A redelivery must be processed, not swallowed by the dedup check.
		doAnswer(i -> null).when(modelService).save(ref);
		dispatcher.dispatch(BillingPlatform.CHARGEBEE, raw);

		assertEquals("APPLIED", eventStore.get("ev-1").getProcessingStatus());
		assertEquals(Integer.valueOf(2), eventStore.get("ev-1").getAttemptCount());
		assertEquals("ACTIVE", ref.getStatus());
		// The retry must not read as a rival event at the same instant, and must not count as extra
		// movement: the first attempt had already put this very event id on the watermark.
		assertNotNull(ref.getLastSyncedAt());
		assertEquals(Long.valueOf(1L), ref.getEventVersion());
	}

	// ---------------------------------------------------------------- helpers

	private void assertProjectedStatus(final BillingEventType type, final String expectedStatus) throws Exception
	{
		final BillingSubscriptionRefModel ref = givenSubscription("sub-1");
		dispatch(eventOf(type, "ev-1", "sub-1", T1));

		assertEquals(expectedStatus, ref.getStatus());
		verify(modelService).save(ref);
	}

	private void dispatch(final NormalizedBillingEvent event) throws Exception
	{
		when(connector.parseWebhook(raw)).thenReturn(event);
		dispatcher.dispatch(BillingPlatform.CHARGEBEE, raw);
	}

	/** Dispatches with a specific body, so the payload-digest dedup key can be exercised. */
	private void dispatchRaw(final NormalizedBillingEvent event, final String payload) throws Exception
	{
		final RawWebhook body = new RawWebhook(Map.of(), payload, null);
		when(connector.parseWebhook(body)).thenReturn(event);
		dispatcher.dispatch(BillingPlatform.CHARGEBEE, body);
	}

	/** Puts a subscription's watermark at T2, so a later T1 event is stale for it. */
	private void seedNewerEventFor(final String subscriptionId) throws Exception
	{
		dispatch(eventOf(BillingEventType.SUBSCRIPTION_ACTIVATED, "seed-" + subscriptionId, subscriptionId, T2));
	}

	private BillingSubscriptionRefModel givenSubscription(final String externalSubscriptionId)
	{
		final BillingSubscriptionRefModel ref = statefulRef();
		when(ref.getExternalSubscriptionId()).thenReturn(externalSubscriptionId);
		refs.add(ref);
		return ref;
	}

	private void resetStore()
	{
		eventStore.clear();
		refs.clear();
	}

	private SearchResult<?> answerSearch(final FlexibleSearchQuery query)
	{
		final List<Object> rows = new ArrayList<>();
		if (query.getQuery().contains("{BillingWebhookEvent}"))
		{
			final BillingWebhookEventModel found = eventStore.get(query.getQueryParameters().get("eventId"));
			if (found != null)
			{
				rows.add(found);
			}
		}
		else
		{
			final Object wanted = query.getQueryParameters().get("externalSubscriptionId");
			refs.stream().filter(ref -> wanted == null || wanted.equals(ref.getExternalSubscriptionId()))
					.forEach(rows::add);
		}
		@SuppressWarnings("unchecked")
		final SearchResult<Object> result = mock(SearchResult.class);
		when(result.getResult()).thenReturn(rows);
		return result;
	}

	private static NormalizedBillingEvent eventOf(final BillingEventType type, final String eventId,
			final String externalSubscriptionId, final Instant occurredAt)
	{
		return new NormalizedBillingEvent(BillingPlatform.CHARGEBEE, type, eventId, externalSubscriptionId, "cust-1",
				occurredAt, Map.of());
	}

	/**
	 * A mock whose setters are readable back through its getters. Only the attributes the dispatcher
	 * touches are wired.
	 */
	private static BillingSubscriptionRefModel statefulRef()
	{
		final Map<String, Object> state = new HashMap<>();
		final BillingSubscriptionRefModel ref = mock(BillingSubscriptionRefModel.class);
		doAnswer(i -> { state.put("status", i.getArgument(0)); return null; }).when(ref).setStatus(any());
		when(ref.getStatus()).thenAnswer(i -> state.get("status"));
		doAnswer(i -> { state.put("eventId", i.getArgument(0)); return null; }).when(ref).setLastAppliedEventId(any());
		when(ref.getLastAppliedEventId()).thenAnswer(i -> state.get("eventId"));
		doAnswer(i -> { state.put("eventAt", i.getArgument(0)); return null; }).when(ref).setLastAppliedEventAt(any());
		when(ref.getLastAppliedEventAt()).thenAnswer(i -> state.get("eventAt"));
		doAnswer(i -> { state.put("version", i.getArgument(0)); return null; }).when(ref).setEventVersion(any());
		when(ref.getEventVersion()).thenAnswer(i -> state.get("version"));
		doAnswer(i -> { state.put("syncedAt", i.getArgument(0)); return null; }).when(ref).setLastSyncedAt(any());
		when(ref.getLastSyncedAt()).thenAnswer(i -> state.get("syncedAt"));
		return ref;
	}

	private static BillingWebhookEventModel statefulEvent()
	{
		final Map<String, Object> state = new HashMap<>();
		final BillingWebhookEventModel record = mock(BillingWebhookEventModel.class);
		doAnswer(i -> { state.put("eventId", i.getArgument(0)); return null; }).when(record).setEventId(any());
		when(record.getEventId()).thenAnswer(i -> state.get("eventId"));
		doAnswer(i -> { state.put("status", i.getArgument(0)); return null; }).when(record).setProcessingStatus(any());
		when(record.getProcessingStatus()).thenAnswer(i -> state.get("status"));
		doAnswer(i -> { state.put("attempts", i.getArgument(0)); return null; }).when(record).setAttemptCount(any());
		when(record.getAttemptCount()).thenAnswer(i -> state.get("attempts"));
		doAnswer(i -> { state.put("lastError", i.getArgument(0)); return null; }).when(record).setLastError(any());
		when(record.getLastError()).thenAnswer(i -> state.get("lastError"));
		return record;
	}
}
