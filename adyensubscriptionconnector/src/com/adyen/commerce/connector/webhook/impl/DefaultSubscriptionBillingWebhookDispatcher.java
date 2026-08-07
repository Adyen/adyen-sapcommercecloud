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

import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adyen.commerce.connector.dto.BillingEventType;
import com.adyen.commerce.connector.dto.NormalizedBillingEvent;
import com.adyen.commerce.connector.dto.RawWebhook;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.BillingException;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;
import com.adyen.commerce.connector.model.BillingWebhookEventModel;
import com.adyen.commerce.connector.registry.SubscriptionBillingConnectorRegistry;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;
import com.adyen.commerce.connector.webhook.SubscriptionBillingWebhookDispatcher;

import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;

/**
 * Default dispatcher. Verifies and normalizes an inbound webhook via the connector, then applies it to
 * the local {@code BillingSubscriptionRef} under the agreed source-of-truth rule: <b>the billing
 * platform is authoritative for subscription status; the local status is a projection of it and never
 * authoritative.</b>
 *
 * <p>Three rules decide whether a delivery may be applied:
 * <ol>
 * <li><b>Duplicate</b> — every delivery is recorded as a {@code BillingWebhookEvent} keyed on the
 * platform's own event id. A redelivery of an id that already reached a terminal outcome is a no-op.
 * The unique index on {@code (platform, eventId)} — not the lookup — is what actually guarantees this,
 * so two concurrent deliveries cannot both apply.</li>
 * <li><b>Stale</b> — an event older than the last applied one is discarded rather than applied, so a
 * late-arriving old event cannot overwrite newer platform state.</li>
 * <li><b>Projection</b> — an accepted event maps the platform's state onto the ref and advances the
 * watermark ({@code lastAppliedEventAt}/{@code lastAppliedEventId}/{@code eventVersion}).</li>
 * </ol>
 *
 * <p>Where ordering is genuinely undecidable — two distinct events bearing the same platform timestamp,
 * which Chargebee's whole-second granularity makes realistic — this does not guess. It applies neither,
 * and clears {@code lastSyncedAt} so the pull-based reconciliation sweep re-fetches authoritative state.
 * That sweep is P4.1b; until it exists, such a ref stays at the first-applied value and is flagged in
 * the log. Retry and dead-letter handling for {@code FAILED} events is P4.4.
 */
public class DefaultSubscriptionBillingWebhookDispatcher implements SubscriptionBillingWebhookDispatcher
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultSubscriptionBillingWebhookDispatcher.class);

	protected static final String PROCESSING_RECEIVED = "RECEIVED";
	protected static final String PROCESSING_APPLIED = "APPLIED";
	protected static final String PROCESSING_SKIPPED_STALE = "SKIPPED_STALE";
	protected static final String PROCESSING_SKIPPED_AMBIGUOUS = "SKIPPED_AMBIGUOUS";
	protected static final String PROCESSING_SKIPPED_UNKNOWN_SUBSCRIPTION = "SKIPPED_UNKNOWN_SUBSCRIPTION";
	protected static final String PROCESSING_FAILED = "FAILED";

	/**
	 * Outcomes that mean "this delivery is finished, do not process it again". {@code RECEIVED} and
	 * {@code FAILED} are deliberately absent: an attempt that never reached a verdict must stay eligible
	 * for the platform's own redelivery, otherwise a transient failure would be silently swallowed
	 * forever by the dedup check that was supposed to protect us.
	 */
	private static final Set<String> TERMINAL_OUTCOMES = Set.of(PROCESSING_APPLIED, PROCESSING_SKIPPED_STALE,
			PROCESSING_SKIPPED_AMBIGUOUS, PROCESSING_SKIPPED_UNKNOWN_SUBSCRIPTION);

	private SubscriptionBillingConnectorRegistry connectorRegistry;
	private FlexibleSearchService flexibleSearchService;
	private ModelService modelService;
	private Clock clock = Clock.systemUTC();

	@Override
	public NormalizedBillingEvent dispatch(final BillingPlatform platform, final RawWebhook raw) throws BillingException
	{
		final SubscriptionBillingConnector connector = connectorRegistry.getConnector(platform);
		final NormalizedBillingEvent event = connector.parseWebhook(raw);
		reconcile(event);
		return event;
	}

	protected void reconcile(final NormalizedBillingEvent event)
	{
		if (event == null)
		{
			return;
		}

		final String dedupKey = dedupKey(event);
		final Optional<BillingWebhookEventModel> alreadySeen = findEvent(event.platform(), dedupKey);
		if (alreadySeen.isPresent() && isTerminal(alreadySeen.get().getProcessingStatus()))
		{
			LOG.info("Ignoring duplicate delivery of {} event '{}' on platform {} (already {})", event.type(), dedupKey,
					event.platform(), alreadySeen.get().getProcessingStatus());
			return;
		}

		// Claim the id before touching subscription state. On a concurrent double delivery one of the two
		// saves loses on the unique index, and that loser must not go on to apply the event a second time.
		final BillingWebhookEventModel record = alreadySeen.orElseGet(() -> newEventRecord(event, dedupKey));
		record.setAttemptCount(attemptCount(record) + 1);
		record.setProcessingStatus(PROCESSING_RECEIVED);
		if (!claim(record, event, dedupKey))
		{
			return;
		}

		try
		{
			apply(event, record);
		}
		catch (final RuntimeException e)
		{
			// Left non-terminal on purpose so a redelivery is still processed. P4.4 turns repeated
			// failures into a dead letter; today the platform's own retry is the recovery path.
			LOG.error("Failed to apply {} event '{}' on platform {}", event.type(), dedupKey, event.platform(), e);
			record.setProcessingStatus(PROCESSING_FAILED);
			record.setLastError(describe(e));
			modelService.save(record);
			throw e;
		}
	}

	/**
	 * Persists the dedup row. A failure here is read as "another delivery of this id got there first",
	 * which is exactly what the unique index on {@code (platform, eventId)} is for.
	 */
	protected boolean claim(final BillingWebhookEventModel record, final NormalizedBillingEvent event, final String dedupKey)
	{
		try
		{
			modelService.save(record);
			return true;
		}
		catch (final RuntimeException e)
		{
			LOG.info("Concurrent delivery of {} event '{}' on platform {} already claimed this id — skipping",
					event.type(), dedupKey, event.platform(), e);
			return false;
		}
	}

	protected void apply(final NormalizedBillingEvent event, final BillingWebhookEventModel record)
	{
		final Optional<BillingSubscriptionRefModel> found = event.externalSubscriptionId() == null
				? Optional.<BillingSubscriptionRefModel> empty()
				: findByExternalId(event.platform(), event.externalSubscriptionId());
		if (found.isEmpty())
		{
			LOG.warn("Received {} event for unknown subscription {} on platform {}", event.type(),
					event.externalSubscriptionId(), event.platform());
			record.setProcessingStatus(PROCESSING_SKIPPED_UNKNOWN_SUBSCRIPTION);
			modelService.save(record);
			return;
		}

		final BillingSubscriptionRefModel ref = found.get();
		record.setSubscriptionRef(ref);

		switch (ordering(event, ref, record.getEventId()))
		{
			case STALE:
				LOG.info("Discarding stale {} event '{}' for subscription {}: occurred {}, last applied {}",
						event.type(), record.getEventId(), event.externalSubscriptionId(), event.occurredAt(),
						ref.getLastAppliedEventAt());
				record.setProcessingStatus(PROCESSING_SKIPPED_STALE);
				modelService.save(record);
				return;

			case UNDECIDABLE:
				// Do not guess an order the platform did not give us. Marking the projection unconfirmed
				// hands the decision to the re-fetch sweep, which reads authoritative state directly.
				LOG.warn("Cannot order {} event '{}' for subscription {} against the last applied event "
						+ "(both at {}) — flagging the reference for re-fetch instead of guessing",
						event.type(), record.getEventId(), event.externalSubscriptionId(), event.occurredAt());
				record.setProcessingStatus(PROCESSING_SKIPPED_AMBIGUOUS);
				ref.setLastSyncedAt(null);
				modelService.save(ref);
				modelService.save(record);
				return;

			default:
				break;
		}

		project(event, ref, record);
		record.setProcessingStatus(PROCESSING_APPLIED);
		modelService.save(ref);
		modelService.save(record);
	}

	/**
	 * Projects platform state onto the local reference and advances the watermark. The watermark moves for
	 * every accepted event, including one whose type carries no status (a payment-method change, say): it
	 * still proves the platform's state as of that moment, so an older event arriving afterwards is stale.
	 */
	protected void project(final NormalizedBillingEvent event, final BillingSubscriptionRefModel ref,
			final BillingWebhookEventModel record)
	{
		final String newStatus = mapStatus(event.type());
		if (newStatus != null)
		{
			ref.setStatus(newStatus);
		}
		// Only a genuinely new event counts as a change. Re-applying the one already on the watermark —
		// which happens when a previous attempt persisted the reference but not its event row — must leave
		// the counter alone, or a retry would look like additional state movement to anyone watching it.
		if (!record.getEventId().equals(ref.getLastAppliedEventId()))
		{
			ref.setEventVersion(eventVersion(ref) + 1L);
		}
		ref.setLastAppliedEventId(record.getEventId());
		if (event.occurredAt() != null)
		{
			ref.setLastAppliedEventAt(Date.from(event.occurredAt()));
		}
		ref.setLastSyncedAt(now());
	}

	/**
	 * Where this delivery sits relative to the last one applied to the reference.
	 */
	protected Ordering ordering(final NormalizedBillingEvent event, final BillingSubscriptionRefModel ref,
			final String dedupKey)
	{
		if (dedupKey.equals(ref.getLastAppliedEventId()))
		{
			// A retry of the very event already on the watermark. It is not a rival for the same instant,
			// so the ambiguity rule below must not fire — re-applying it just restates what it already said.
			return Ordering.NEWER;
		}
		final Date lastApplied = ref.getLastAppliedEventAt();
		if (lastApplied == null)
		{
			return Ordering.NEWER;
		}
		if (event.occurredAt() == null)
		{
			// No timestamp to compare against a watermark that exists — same problem as a tie.
			return Ordering.UNDECIDABLE;
		}
		final Instant last = lastApplied.toInstant();
		if (event.occurredAt().isBefore(last))
		{
			return Ordering.STALE;
		}
		if (event.occurredAt().equals(last))
		{
			return Ordering.UNDECIDABLE;
		}
		return Ordering.NEWER;
	}

	/**
	 * The platform's own event id where there is one. A connector that cannot supply one still gets replay
	 * protection, from a key derived from the content that identifies the delivery — a genuine redelivery
	 * reproduces it exactly. The prefix keeps the two kinds distinguishable to an operator reading the table.
	 */
	protected String dedupKey(final NormalizedBillingEvent event)
	{
		if (event.eventId() != null && !event.eventId().isBlank())
		{
			return event.eventId();
		}
		final String derived = "derived:" + event.type() + ':' + event.externalSubscriptionId() + ':'
				+ (event.occurredAt() == null ? "no-timestamp" : Long.toString(event.occurredAt().getEpochSecond()));
		LOG.warn("Connector for platform {} produced an event without a platform event id; "
				+ "deduplicating on derived key '{}' instead", event.platform(), derived);
		return derived;
	}

	protected BillingWebhookEventModel newEventRecord(final NormalizedBillingEvent event, final String dedupKey)
	{
		final BillingWebhookEventModel record = modelService.create(BillingWebhookEventModel.class);
		record.setPlatform(event.platform());
		record.setEventId(dedupKey);
		record.setEventType(event.type() == null ? null : event.type().name());
		record.setExternalSubscriptionId(event.externalSubscriptionId());
		record.setOccurredAt(event.occurredAt() == null ? null : Date.from(event.occurredAt()));
		record.setReceivedAt(now());
		return record;
	}

	protected String mapStatus(final BillingEventType type)
	{
		if (type == null)
		{
			return null;
		}
		switch (type)
		{
			case SUBSCRIPTION_ACTIVATED:
			case SUBSCRIPTION_RENEWED:
			case SUBSCRIPTION_RESUMED:
			case INVOICE_PAID:
				return "ACTIVE";
			case SUBSCRIPTION_CANCELLED:
				return "CANCELLED";
			case SUBSCRIPTION_PAUSED:
				return "PAUSED";
			case INVOICE_PAYMENT_FAILED:
				return "PAST_DUE";
			default:
				return null;
		}
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

	protected enum Ordering
	{
		NEWER, STALE, UNDECIDABLE
	}

	private static boolean isTerminal(final String processingStatus)
	{
		// Set.of(...) rejects a null probe, and a half-written record legitimately has no status yet.
		return processingStatus != null && TERMINAL_OUTCOMES.contains(processingStatus);
	}

	private static int attemptCount(final BillingWebhookEventModel record)
	{
		return record.getAttemptCount() == null ? 0 : record.getAttemptCount().intValue();
	}

	private static long eventVersion(final BillingSubscriptionRefModel ref)
	{
		return ref.getEventVersion() == null ? 0L : ref.getEventVersion().longValue();
	}

	private static String describe(final RuntimeException e)
	{
		return e.getClass().getName() + ": " + e.getMessage();
	}

	private Date now()
	{
		return Date.from(clock.instant());
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

	public void setClock(final Clock clock)
	{
		this.clock = clock;
	}
}
