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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Date;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adyen.commerce.connector.dto.BillingEventType;
import com.adyen.commerce.connector.dto.NormalizedBillingEvent;
import com.adyen.commerce.connector.dto.RawWebhook;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.BillingException;
import com.adyen.commerce.connector.exception.RetryableBillingException;
import com.adyen.commerce.connector.log.ConnectorLogContext;
import com.adyen.commerce.connector.log.ConnectorLogEvent;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;
import com.adyen.commerce.connector.model.BillingWebhookEventApplicationModel;
import com.adyen.commerce.connector.model.BillingWebhookEventModel;
import com.adyen.commerce.connector.reconciliation.SubscriptionReconciliationService;
import com.adyen.commerce.connector.registry.SubscriptionBillingConnectorRegistry;
import com.adyen.commerce.connector.retry.BillingRetryPolicy;
import com.adyen.commerce.connector.retry.RetryVerdict;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;
import com.adyen.commerce.connector.webhook.SubscriptionBillingWebhookDispatcher;

import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;

/**
 * Verifies and deduplicates an inbound webhook, resolves the subscriptions it concerns and reads each
 * subscription's current state from the billing platform.
 *
 * <p>An event says only that a resource may have changed; {@link SubscriptionReconciliationService}
 * derives the local state from a live platform snapshot, so delivery ordering carries no state rule. The
 * platform event id is the deduplication key, and {@code BillingWebhookEventApplication} records the
 * independent result for every subscription behind a multi-subscription invoice.</p>
 *
 * <p>Retries belong to the platform: a delivery answered with an error is sent again, and
 * {@link com.adyen.commerce.connector.retry.BillingRetryPolicy} bounds how long that lasts. Once it stops,
 * the delivery is marked {@code DEAD_LETTER} and the caller is answered successfully, so the platform stops
 * redelivering into a row that would discard it. An event naming a local reference that does not exist is
 * bounded the same way and recorded as skipped.</p>
 */
public class DefaultSubscriptionBillingWebhookDispatcher implements SubscriptionBillingWebhookDispatcher
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultSubscriptionBillingWebhookDispatcher.class);

	/** One event name for every delivery, so the outcomes are countable against each other. */
	private static final String EVENT_DISPATCH = "webhook_dispatched";

	protected static final int MAX_STORED_PAYLOAD_LENGTH = 8000;

	protected static final String PROCESSING_RECEIVED = "RECEIVED";
	protected static final String PROCESSING_RECONCILED = "RECONCILED";
	protected static final String PROCESSING_SKIPPED_NO_SUBSCRIPTION = "SKIPPED_NO_SUBSCRIPTION";
	protected static final String PROCESSING_SKIPPED_UNSUPPORTED = "SKIPPED_UNSUPPORTED";
	protected static final String PROCESSING_SKIPPED_UNKNOWN_SUBSCRIPTION = "SKIPPED_UNKNOWN_SUBSCRIPTION";
	protected static final String PROCESSING_RETRYABLE_UNKNOWN_SUBSCRIPTION = "RETRYABLE_UNKNOWN_SUBSCRIPTION";
	protected static final String PROCESSING_FAILED = "FAILED";
	protected static final String PROCESSING_DEAD_LETTER = "DEAD_LETTER";

	/**
	 * Outcomes that finish a delivery and bar it from being processed again. {@code RECEIVED} and
	 * {@code FAILED} are absent so the platform's own redelivery is still processed; {@code DEAD_LETTER} is
	 * present so a redelivery arriving after the decision to stop is dropped.
	 */
	private static final Set<String> TERMINAL_OUTCOMES = Set.of(PROCESSING_RECONCILED,
			PROCESSING_SKIPPED_NO_SUBSCRIPTION, PROCESSING_SKIPPED_UNSUPPORTED, PROCESSING_DEAD_LETTER);

	/**
	 * Event types whose subject is the subscription itself, and for which a missing local reference may
	 * therefore still be on its way. Membership makes the dispatcher wait, at the cost of forced errors and a
	 * stored webhook body for every subscription on the platform that is not managed locally, so only types
	 * where the create/webhook race is real belong here - which is why
	 * {@code SUBSCRIPTION_CANCELLATION_SCHEDULED} and {@code SUBSCRIPTION_CANCELLATION_REMOVED} are absent
	 * although both are subscription-shaped.
	 */
	private static final Set<BillingEventType> SUBSCRIPTION_SCOPED_TYPES = EnumSet.of(
			BillingEventType.SUBSCRIPTION_CREATED, BillingEventType.SUBSCRIPTION_ACTIVATED,
			BillingEventType.SUBSCRIPTION_UPDATED, BillingEventType.SUBSCRIPTION_RENEWED,
			BillingEventType.SUBSCRIPTION_CANCELLED, BillingEventType.SUBSCRIPTION_EXPIRED,
			BillingEventType.SUBSCRIPTION_PAUSED, BillingEventType.SUBSCRIPTION_RESUMED,
			BillingEventType.SUBSCRIPTION_CHANGE_SCHEDULED, BillingEventType.SUBSCRIPTION_PAUSE_SCHEDULED,
			BillingEventType.SUBSCRIPTION_PAUSE_UPDATED, BillingEventType.SUBSCRIPTION_PAUSE_CANCELLED);

	private SubscriptionBillingConnectorRegistry connectorRegistry;
	private FlexibleSearchService flexibleSearchService;
	private ModelService modelService;
	private BillingRetryPolicy retryPolicy;
	private Clock clock = Clock.systemUTC();
	private SubscriptionReconciliationService reconciliationService;

	@Override
	public NormalizedBillingEvent dispatch(final BillingPlatform platform, final RawWebhook raw) throws BillingException
	{
		final long startedAt = System.nanoTime();
		final SubscriptionBillingConnector connector = connectorRegistry.getConnector(platform);
		final NormalizedBillingEvent event = connector.parseWebhook(raw);

		// Correlated on the platform's own event id, which is also the delivery's deduplication key, so a
		// redelivery and the original it repeats share one identifier.
		try (ConnectorLogContext correlation = ConnectorLogContext.correlate(event == null ? null : event.eventId()))
		{
			try
			{
				reconcile(event, connector, raw);
				dispatchOutcome(platform, event).success(startedAt).info(LOG);
				return event;
			}
			catch (final BillingException | RuntimeException e)
			{
				final BillingException billingFailure = e instanceof BillingException billing ? billing : null;
				dispatchOutcome(platform, event)
						.field("exception_class", e.getClass().getName())
						.failure(startedAt, billingFailure)
						.warn(LOG);
				throw e;
			}
		}
	}

	/**
	 * Bounds the stored body, marking the cut so that a truncated body is not read as the whole of what
	 * arrived.
	 */
	protected String truncatePayload(final String payload)
	{
		if (payload == null || payload.length() <= MAX_STORED_PAYLOAD_LENGTH)
		{
			return payload;
		}
		return payload.substring(0, MAX_STORED_PAYLOAD_LENGTH) + "... [truncated, " + payload.length() + " chars]";
	}

	/**
	 * The shared half of the one line every delivery produces, whichever way it ends.
	 *
	 * <p>A {@code null} event is the connector saying the vendor sent something it does not act on: a
	 * successful delivery, and not a subscription change.</p>
	 */
	protected ConnectorLogEvent dispatchOutcome(final BillingPlatform platform, final NormalizedBillingEvent event)
	{
		final ConnectorLogEvent line = ConnectorLogEvent.of(EVENT_DISPATCH).platform(platform);
		if (event == null)
		{
			return line.outcome(ConnectorLogEvent.OUTCOME_IGNORED).reason("event type not acted on by the connector");
		}
		return line.field("normalized_event_type", ConnectorLogContext.code(event.type()))
				.field("event_id", event.eventId())
				.field("external_subscription_id", event.externalSubscriptionId());
	}

	protected void reconcile(final NormalizedBillingEvent event, final SubscriptionBillingConnector connector,
			final RawWebhook raw) throws BillingException
	{
		if (event == null)
		{
			return;
		}

		final String dedupKey = dedupKey(event, raw);
		final Optional<BillingWebhookEventModel> alreadySeen = findEvent(event.platform(), dedupKey);
		if (alreadySeen.isPresent() && isTerminal(alreadySeen.get().getProcessingStatus()))
		{
			LOG.info("Ignoring duplicate delivery of {} event '{}' on platform {} (already {})", event.type(), dedupKey,
					event.platform(), alreadySeen.get().getProcessingStatus());
			return;
		}

		final BillingWebhookEventModel record = alreadySeen.orElseGet(() -> newEventRecord(event, dedupKey));
		record.setAttemptCount(attemptCount(record) + 1);
		record.setProcessingStatus(PROCESSING_RECEIVED);
		record.setLastError(null);
		// The stored body belongs to the error; a delivery that succeeds leaves none behind.
		record.setPayload(null);
		if (!claim(record, event, dedupKey))
		{
			return;
		}

		try
		{
			apply(event, record, connector);
		}
		catch (final RuntimeException | BillingException e)
		{
			recordFailure(event, record, dedupKey, raw, e);
		}
	}

	/**
	 * Marks a failed delivery and decides whether the platform should be invited to send it again.
	 *
	 * @throws BillingException  rethrown unchanged when the delivery is still due a retry, so the caller
	 *                           answers the platform with an error and the platform redelivers
	 * @throws RuntimeException  likewise
	 */
	protected void recordFailure(final NormalizedBillingEvent event, final BillingWebhookEventModel record,
			final String dedupKey, final RawWebhook raw, final Exception failure) throws BillingException
	{
		final RetryVerdict verdict = retryPolicy.decide(failure, attemptCount(record), clock.instant());
		record.setLastError(describe(failure));
		// Stored only on failure; see the attribute's own description for why a success keeps no body.
		record.setPayload(truncatePayload(raw == null ? null : raw.payload()));

		if (verdict.retry())
		{
			// Left non-terminal on purpose so the redelivery is processed rather than dropped as a duplicate.
			LOG.error("Failed to apply {} event '{}' on platform {} ({}); leaving it open for redelivery.",
					event.type(), dedupKey, event.platform(), verdict.reason(), failure);
			record.setProcessingStatus(PROCESSING_FAILED);
			modelService.save(record);
			rethrow(failure);
			return;
		}

		record.setProcessingStatus(PROCESSING_DEAD_LETTER);
		record.setDeadLetteredAt(now());
		modelService.save(record);
		// Not rethrown: the platform is answered with a success so that it stops redelivering an event this
		// row now discards. The row, and this line, are the record that it was lost.
		LOG.error("DEAD LETTER: giving up on {} event '{}' for subscription {} on platform {} — {}. It will not be "
				+ "applied and further redeliveries will be discarded; this needs an operator.", event.type(), dedupKey,
				event.externalSubscriptionId(), event.platform(), verdict.reason(), failure);
	}

	/**
	 * Rethrows the original failure with its own type intact, so the controller keeps mapping a retryable
	 * {@link BillingException} to a different status than a terminal one.
	 */
	private static void rethrow(final Exception failure) throws BillingException
	{
		if (failure instanceof BillingException billingException)
		{
			throw billingException;
		}
		throw (RuntimeException) failure;
	}

	/**
	 * Persists the dedup row, which is how this delivery claims the event id.
	 *
	 * <p>A save failure counts as a lost race only when the row really is there afterwards; anything else -
	 * a lock timeout, a dropped connection, a truncation - is rethrown, because answering the platform with
	 * a success would lose the delivery with no row to show for it.</p>
	 */
	protected boolean claim(final BillingWebhookEventModel record, final NormalizedBillingEvent event,
			final String dedupKey)
	{
		try
		{
			modelService.save(record);
			return true;
		}
		catch (final RuntimeException e)
		{
			if (findEvent(event.platform(), dedupKey).isPresent())
			{
				LOG.info("Concurrent delivery of {} event '{}' on platform {} already claimed this id — skipping",
						event.type(), dedupKey, event.platform(), e);
				return false;
			}
			throw e;
		}
	}

	protected void apply(final NormalizedBillingEvent event, final BillingWebhookEventModel record,
			final SubscriptionBillingConnector connector) throws BillingException
	{
		final List<String> subscriptionIds = resolveSubscriptionIds(event, connector);
		if (subscriptionIds.isEmpty())
		{
			final String outcome = event.type() == BillingEventType.UNKNOWN
					? PROCESSING_SKIPPED_UNSUPPORTED
					: PROCESSING_SKIPPED_NO_SUBSCRIPTION;
			record.setProcessingStatus(outcome);
			modelService.save(record);
			return;
		}

		boolean reconciledAny = false;
		for (final String subscriptionId : subscriptionIds)
		{
			final BillingWebhookEventApplicationModel application = findApplication(record, subscriptionId)
					.orElseGet(() -> newApplication(record, subscriptionId));
			application.setAttemptCount(applicationAttemptCount(application) + 1);
			application.setProcessingStatus(PROCESSING_RECEIVED);
			application.setLastError(null);
			modelService.save(application);

			final Optional<BillingSubscriptionRefModel> found = findByExternalId(event.platform(), subscriptionId);
			if (found.isEmpty())
			{
				handleUnknownSubscription(event, application, subscriptionId);
				continue;
			}

			final BillingSubscriptionRefModel ref = found.get();
			application.setSubscriptionRef(ref);
			if (record.getSubscriptionRef() == null)
			{
				record.setSubscriptionRef(ref);
			}

			try
			{
				final Projection before = Projection.from(ref);
				reconciliationService.reconcile(ref);
				markReconciled(event, record, ref, before.differsFrom(ref));

				application.setProcessingStatus(PROCESSING_RECONCILED);
				application.setReconciledAt(now());
				application.setLastError(null);
				modelService.save(application);
				reconciledAny = true;
			}
			catch (final RuntimeException | BillingException e)
			{
				application.setProcessingStatus(PROCESSING_FAILED);
				application.setLastError(describe(e));
				modelService.save(application);
				throw e;
			}
		}

		record.setProcessingStatus(reconciledAny ? PROCESSING_RECONCILED : PROCESSING_SKIPPED_NO_SUBSCRIPTION);
		record.setLastError(null);
		modelService.save(record);
	}

	/**
	 * Records that this delivery names a subscription with no local reference, and decides whether it is
	 * worth waiting for one.
	 *
	 * <p>The wait is bounded by {@link BillingRetryPolicy}, counted on the per-subscription attempt count of
	 * the application row: no reference is coming for a subscription created in the platform's own panel or
	 * whose local activation was given up on.</p>
	 *
	 * @throws RetryableBillingException while the wait is still justified, so the caller answers the
	 *                                   platform with an error and the platform redelivers
	 */
	protected void handleUnknownSubscription(final NormalizedBillingEvent event,
			final BillingWebhookEventApplicationModel application, final String subscriptionId)
			throws RetryableBillingException
	{
		if (isDirectSubscriptionEvent(event))
		{
			final RetryableBillingException race = new RetryableBillingException("Subscription " + subscriptionId
					+ " is not available locally yet; retrying protects the create/webhook race");
			final RetryVerdict verdict = retryPolicy.decide(race, applicationAttemptCount(application),
					clock.instant());
			if (verdict.retry())
			{
				application.setProcessingStatus(PROCESSING_RETRYABLE_UNKNOWN_SUBSCRIPTION);
				application.setLastError("Local subscription reference does not exist yet");
				modelService.save(application);
				throw race;
			}

			// Not rethrown, for the reason recordFailure() does not rethrow a dead letter: the caller would
			// answer with an error, and the platform would go on redelivering an event this row now refuses.
			LOG.error("Giving up on {} event '{}' for external subscription {} on platform {} — {}. Recording it "
					+ "as a subscription that is not managed locally; if it should have been ours, its local "
					+ "reference was never created and this needs an operator.", event.type(),
					application.getEvent().getEventId(), subscriptionId, event.platform(), verdict.reason());
			application.setProcessingStatus(PROCESSING_SKIPPED_UNKNOWN_SUBSCRIPTION);
			application.setLastError(verdict.reason());
			modelService.save(application);
			return;
		}

		LOG.info("Ignoring {} event for external subscription {} that is not managed locally",
				event.type(), subscriptionId);
		application.setProcessingStatus(PROCESSING_SKIPPED_UNKNOWN_SUBSCRIPTION);
		application.setLastError(null);
		modelService.save(application);
	}

	/**
	 * Whether the event's own subject is the subscription it names, which is the only case where a missing
	 * local reference might still be on its way. The answer comes from {@link BillingEventType} and nothing
	 * else.
	 */
	protected boolean isDirectSubscriptionEvent(final NormalizedBillingEvent event)
	{
		if (event.externalSubscriptionId() == null || event.externalSubscriptionId().isBlank())
		{
			return false;
		}
		return SUBSCRIPTION_SCOPED_TYPES.contains(event.type());
	}

	protected List<String> resolveSubscriptionIds(final NormalizedBillingEvent event,
			final SubscriptionBillingConnector connector) throws BillingException
	{
		if (event.externalSubscriptionId() != null && !event.externalSubscriptionId().isBlank())
		{
			return List.of(event.externalSubscriptionId());
		}
		final List<String> resolved = connector.resolveSubscriptionIds(event);
		return resolved == null ? List.of()
				: resolved.stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
	}

	protected void markReconciled(final NormalizedBillingEvent event, final BillingWebhookEventModel record,
			final BillingSubscriptionRefModel ref, final boolean projectionChanged)
	{
		if (projectionChanged)
		{
			ref.setEventVersion(eventVersion(ref) + 1L);
		}
		ref.setLastAppliedEventId(record.getEventId());
		if (event.occurredAt() != null)
		{
			ref.setLastAppliedEventAt(Date.from(event.occurredAt()));
		}
		modelService.save(ref);
	}

	protected String dedupKey(final NormalizedBillingEvent event, final RawWebhook raw)
	{
		if (event.eventId() != null && !event.eventId().isBlank())
		{
			return event.eventId();
		}
		final String derived = "derived:" + digest(raw == null ? null : raw.payload());
		LOG.warn("Connector for platform {} produced a {} event with no platform event id; deduplicating on '{}'",
				event.platform(), event.type(), derived);
		return derived;
	}

	private static String digest(final String payload)
	{
		try
		{
			final byte[] hash = MessageDigest.getInstance("SHA-256")
					.digest(String.valueOf(payload).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		}
		catch (final NoSuchAlgorithmException e)
		{
			throw new IllegalStateException("SHA-256 is required to deduplicate webhooks without an event id", e);
		}
	}

	protected BillingWebhookEventModel newEventRecord(final NormalizedBillingEvent event, final String dedupKey)
	{
		final BillingWebhookEventModel record = modelService.create(BillingWebhookEventModel.class);
		record.setPlatform(event.platform());
		record.setEventId(dedupKey);
		// NormalizedBillingEvent's compact constructor rejects a null type, so an unrecognised event has
		// already been normalised to UNKNOWN by the time it reaches here.
		record.setEventType(event.type().name());
		record.setExternalSubscriptionId(event.externalSubscriptionId());
		record.setOccurredAt(event.occurredAt() == null ? null : Date.from(event.occurredAt()));
		record.setReceivedAt(now());
		return record;
	}

	protected BillingWebhookEventApplicationModel newApplication(final BillingWebhookEventModel event,
			final String externalSubscriptionId)
	{
		final BillingWebhookEventApplicationModel application = modelService
				.create(BillingWebhookEventApplicationModel.class);
		application.setEvent(event);
		application.setExternalSubscriptionId(externalSubscriptionId);
		return application;
	}

	protected Optional<BillingWebhookEventModel> findEvent(final BillingPlatform platform, final String eventId)
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery("SELECT {pk} FROM {BillingWebhookEvent} "
				+ "WHERE {platform} = ?platform AND {eventId} = ?eventId");
		query.addQueryParameter("platform", platform);
		query.addQueryParameter("eventId", eventId);
		final List<BillingWebhookEventModel> result = flexibleSearchService
				.<BillingWebhookEventModel> search(query).getResult();
		return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
	}

	protected Optional<BillingWebhookEventApplicationModel> findApplication(final BillingWebhookEventModel event,
			final String externalSubscriptionId)
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery(
				"SELECT {pk} FROM {BillingWebhookEventApplication} "
						+ "WHERE {event} = ?event AND {externalSubscriptionId} = ?externalSubscriptionId");
		query.addQueryParameter("event", event);
		query.addQueryParameter("externalSubscriptionId", externalSubscriptionId);
		final List<BillingWebhookEventApplicationModel> result = flexibleSearchService
				.<BillingWebhookEventApplicationModel> search(query).getResult();
		return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
	}

	protected Optional<BillingSubscriptionRefModel> findByExternalId(final BillingPlatform platform,
			final String externalSubscriptionId)
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery("SELECT {pk} FROM {BillingSubscriptionRef} "
				+ "WHERE {platform} = ?platform AND {externalSubscriptionId} = ?externalSubscriptionId");
		query.addQueryParameter("platform", platform);
		query.addQueryParameter("externalSubscriptionId", externalSubscriptionId);
		final List<BillingSubscriptionRefModel> result = flexibleSearchService
				.<BillingSubscriptionRefModel> search(query).getResult();
		return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
	}

	private static boolean isTerminal(final String processingStatus)
	{
		return processingStatus != null && TERMINAL_OUTCOMES.contains(processingStatus);
	}

	private static int attemptCount(final BillingWebhookEventModel record)
	{
		return record.getAttemptCount() == null ? 0 : record.getAttemptCount().intValue();
	}

	private static int applicationAttemptCount(final BillingWebhookEventApplicationModel application)
	{
		return application.getAttemptCount() == null ? 0 : application.getAttemptCount().intValue();
	}

	private static long eventVersion(final BillingSubscriptionRefModel ref)
	{
		return ref.getEventVersion() == null ? 0L : ref.getEventVersion().longValue();
	}

	private static String describe(final Throwable e)
	{
		return e.getClass().getName() + ": " + e.getMessage();
	}

	private Date now()
	{
		return Date.from(clock.instant());
	}

	/** The projection fields taken before a reconciliation; only a real change to them bumps the event version. */
	protected record Projection(String status, String planCode, Integer quantity, Date currentPeriodStart,
			Date currentPeriodEnd, Boolean cancelAtPeriodEnd)
	{
		static Projection from(final BillingSubscriptionRefModel ref)
		{
			return new Projection(ref.getStatus(), ref.getPlanCode(), ref.getQuantity(), ref.getCurrentPeriodStart(),
					ref.getCurrentPeriodEnd(), ref.getCancelAtPeriodEnd());
		}

		boolean differsFrom(final BillingSubscriptionRefModel ref)
		{
			return !Objects.equals(this, from(ref));
		}
	}

	public void setConnectorRegistry(final SubscriptionBillingConnectorRegistry connectorRegistry)
	{
		this.connectorRegistry = connectorRegistry;
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}

	public void setRetryPolicy(final BillingRetryPolicy retryPolicy)
	{
		this.retryPolicy = retryPolicy;
	}

	public void setClock(final Clock clock)
	{
		this.clock = clock;
	}

	public void setReconciliationService(final SubscriptionReconciliationService reconciliationService)
	{
		this.reconciliationService = reconciliationService;
	}
}
