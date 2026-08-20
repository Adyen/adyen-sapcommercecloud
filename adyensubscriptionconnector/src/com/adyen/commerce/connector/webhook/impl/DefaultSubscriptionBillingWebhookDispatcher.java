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
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
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
import com.adyen.commerce.connector.retry.BillingRetryPolicy;
import com.adyen.commerce.connector.retry.RetryVerdict;
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
 * watermark ({@code lastAppliedEventAt}/{@code lastAppliedEventId}/{@code eventVersion}). Only events
 * that actually carry a status take part: the stale and ordering rules are about competing status
 * claims, so an event mapping to no status is recorded and otherwise left to one side.</li>
 * </ol>
 *
 * <p>Where ordering is genuinely undecidable — two distinct events bearing the same platform timestamp,
 * which Chargebee's whole-second granularity makes realistic — this does not guess. It applies neither,
 * and clears {@code lastSyncedAt} so the pull-based reconciliation sweep re-fetches authoritative state.
 * That sweep does not exist yet; until it does, such a ref stays at the first-applied value and is
 * flagged in the log.
 *
 * <h3>Failure, retry and the dead letter</h3>
 * <p>The retries on this path are the platform's, not ours: a delivery that fails is answered with an
 * error and the platform sends it again. This class decides only how long that is allowed to go on, and
 * it asks {@link com.adyen.commerce.connector.retry.BillingRetryPolicy} — the same policy the outbound
 * activation path uses, so the two cannot drift on what counts as worth retrying.</p>
 *
 * <p>Two things end it. A terminal failure ends it at once, because a delivery that will fail the same
 * way on replay gains nothing from being replayed. Otherwise the attempt count does, once it passes the
 * policy's cut-off. Either way the delivery is marked {@code DEAD_LETTER} — a terminal outcome, so a
 * later redelivery is dropped against it — and, crucially, the caller is <em>not</em> told it failed.
 * Answering with an error would keep a platform that has been told nothing is wrong redelivering into a
 * row that now discards it, which is a busy way of losing the event twice over.</p>
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
	protected static final String PROCESSING_DEAD_LETTER = "DEAD_LETTER";

	/**
	 * Outcomes that mean "this delivery is finished, do not process it again". {@code RECEIVED} and
	 * {@code FAILED} are deliberately absent: an attempt that never reached a verdict must stay eligible
	 * for the platform's own redelivery, otherwise a transient failure would be silently swallowed
	 * forever by the dedup check that was supposed to protect us.
	 *
	 * <p>{@code DEAD_LETTER} is present for the opposite reason. It is the deliberate decision to stop, so
	 * a redelivery arriving after it must be dropped rather than restarting a series of attempts that has
	 * already been given up on and reported.</p>
	 */
	private static final Set<String> TERMINAL_OUTCOMES = Set.of(PROCESSING_APPLIED, PROCESSING_SKIPPED_STALE,
			PROCESSING_SKIPPED_AMBIGUOUS, PROCESSING_SKIPPED_UNKNOWN_SUBSCRIPTION, PROCESSING_DEAD_LETTER);

	/** Weakest to strongest; see {@link #strongest(String, String)}. */
	private static final List<String> OUTCOME_PRECEDENCE = List.of(PROCESSING_SKIPPED_UNKNOWN_SUBSCRIPTION,
			PROCESSING_SKIPPED_STALE, PROCESSING_SKIPPED_AMBIGUOUS, PROCESSING_APPLIED);

	private SubscriptionBillingConnectorRegistry connectorRegistry;
	private FlexibleSearchService flexibleSearchService;
	private ModelService modelService;
	private BillingRetryPolicy retryPolicy;
	private Clock clock = Clock.systemUTC();

	@Override
	public NormalizedBillingEvent dispatch(final BillingPlatform platform, final RawWebhook raw) throws BillingException
	{
		final SubscriptionBillingConnector connector = connectorRegistry.getConnector(platform);
		final NormalizedBillingEvent event = connector.parseWebhook(raw);
		reconcile(event, connector, raw);
		return event;
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
			apply(event, record, connector);
		}
		catch (final RuntimeException | BillingException e)
		{
			recordFailure(event, record, dedupKey, e);
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
			final String dedupKey, final Exception failure) throws BillingException
	{
		final RetryVerdict verdict = retryPolicy.decide(failure, attemptCount(record), clock.instant());
		record.setLastError(describe(failure));

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
		// Not rethrown. The caller answers the platform with a success it did not earn, on purpose: this
		// delivery will never be processed now, and letting the platform keep sending it would only produce
		// traffic that the dedup check discards. The row, and this line, are the record that it was lost.
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
	 * <p>A failure here is only treated as "another delivery got there first" if the row really is there
	 * afterwards. Anything else — a lock timeout, a dropped connection, a truncation — is rethrown. Reading
	 * every save failure as a lost race would be worse than the race it guards against: the caller would
	 * answer the platform with a success, the platform would never redeliver, and no row would exist to
	 * show that anything was lost.
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
			if (findEvent(event.platform(), dedupKey).isPresent())
			{
				LOG.info("Concurrent delivery of {} event '{}' on platform {} already claimed this id — skipping",
						event.type(), dedupKey, event.platform(), e);
				return false;
			}
			LOG.error("Could not record {} event '{}' on platform {}; failing so the platform redelivers",
					event.type(), dedupKey, event.platform(), e);
			throw e;
		}
	}

	protected void apply(final NormalizedBillingEvent event, final BillingWebhookEventModel record,
			final SubscriptionBillingConnector connector) throws BillingException
	{
		final List<String> subscriptionIds = resolveSubscriptionIds(event, connector);
		if (subscriptionIds.isEmpty())
		{
			LOG.warn("Received {} event '{}' on platform {} that names no subscription and could not be resolved to one",
					event.type(), record.getEventId(), event.platform());
			record.setProcessingStatus(PROCESSING_SKIPPED_UNKNOWN_SUBSCRIPTION);
			modelService.save(record);
			return;
		}

		String outcome = null;
		for (final String subscriptionId : subscriptionIds)
		{
			final Optional<BillingSubscriptionRefModel> found = findByExternalId(event.platform(), subscriptionId);
			if (found.isEmpty())
			{
				LOG.warn("Received {} event for unknown subscription {} on platform {}", event.type(), subscriptionId,
						event.platform());
				outcome = strongest(outcome, PROCESSING_SKIPPED_UNKNOWN_SUBSCRIPTION);
				continue;
			}
			final BillingSubscriptionRefModel ref = found.get();
			if (record.getSubscriptionRef() == null)
			{
				record.setSubscriptionRef(ref);
			}
			outcome = strongest(outcome, applyToRef(event, ref, record));
		}

		record.setProcessingStatus(outcome);
		modelService.save(record);
	}

	/**
	 * The subscriptions this event applies to. An event that names its own subscription is taken at its
	 * word; only one that does not costs a connector round-trip — and by this point the event id is
	 * already claimed, so a redelivery never pays for that lookup twice.
	 */
	protected List<String> resolveSubscriptionIds(final NormalizedBillingEvent event,
			final SubscriptionBillingConnector connector) throws BillingException
	{
		if (event.externalSubscriptionId() != null && !event.externalSubscriptionId().isBlank())
		{
			return List.of(event.externalSubscriptionId());
		}
		final List<String> resolved = connector.resolveSubscriptionIds(event);
		return resolved == null ? List.of()
				: resolved.stream().filter(id -> id != null && !id.isBlank()).toList();
	}

	/**
	 * Applies one event to one reference and reports what happened to it.
	 */
	protected String applyToRef(final NormalizedBillingEvent event, final BillingSubscriptionRefModel ref,
			final BillingWebhookEventModel record)
	{
		if (mapStatus(event.type()) == null)
		{
			// Carries no status, so it neither competes with nor supersedes anything. Recorded as handled
			// and the reference is left exactly as it was — deliberately without consulting the ordering
			// rules, which only make sense between events that both claim a status.
			return PROCESSING_APPLIED;
		}

		switch (ordering(event, ref, record.getEventId()))
		{
			case STALE:
				LOG.info("Discarding stale {} event '{}' for subscription {}: occurred {}, last applied {}",
						event.type(), record.getEventId(), ref.getExternalSubscriptionId(), event.occurredAt(),
						ref.getLastAppliedEventAt());
				return PROCESSING_SKIPPED_STALE;

			case UNDECIDABLE:
				// Do not guess an order the platform did not give us. Marking the projection unconfirmed
				// hands the decision to the re-fetch sweep, which reads authoritative state directly.
				LOG.warn("Cannot order {} event '{}' for subscription {} against the last applied event "
						+ "(both at {}) — flagging the reference for re-fetch instead of guessing",
						event.type(), record.getEventId(), ref.getExternalSubscriptionId(), event.occurredAt());
				ref.setLastSyncedAt(null);
				modelService.save(ref);
				return PROCESSING_SKIPPED_AMBIGUOUS;

			default:
				project(event, ref, record);
				modelService.save(ref);
				return PROCESSING_APPLIED;
		}
	}

	/**
	 * Projects platform state onto the local reference and advances the watermark.
	 *
	 * <p>The watermark tracks status specifically, so only an event that carries a status moves it. An
	 * event type this connector maps to nothing — a payment-method change, an unrecognised type — says
	 * nothing about the subscription's status and must leave it alone. Moving it for those would be
	 * actively harmful on platforms whose timestamps are whole seconds: a status-less event landing in the
	 * same second as a real one would make the real one look like an unresolvable tie and drop it, and one
	 * arriving later would make a genuinely newer status change look stale.
	 *
	 * @return whether anything was actually projected
	 */
	protected boolean project(final NormalizedBillingEvent event, final BillingSubscriptionRefModel ref,
			final BillingWebhookEventModel record)
	{
		final String newStatus = mapStatus(event.type());
		if (newStatus == null)
		{
			return false;
		}
		ref.setStatus(newStatus);
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
		return true;
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
	protected String dedupKey(final NormalizedBillingEvent event, final RawWebhook raw)
	{
		if (event.eventId() != null && !event.eventId().isBlank())
		{
			return event.eventId();
		}
		// The key has to come from the delivery itself, because the normalized fields are not enough to tell
		// two deliveries apart: an event that names no subscription (an invoice, say) contributes nothing
		// there, and a whole-second timestamp is shared by everything that happened in that second. Two
		// unrelated invoices would then collide and the second would be discarded as a duplicate. The body
		// is the one thing a genuine redelivery reproduces exactly and a different event does not.
		final String derived = "derived:" + digest(raw == null ? null : raw.payload());
		LOG.warn("Connector for platform {} produced a {} event with no platform event id; deduplicating on "
				+ "the payload digest '{}' instead", event.platform(), event.type(), derived);
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

	/**
	 * One event can touch several references and fare differently on each. The row records the strongest
	 * thing that happened: if any reference took the event it was applied, and among the refusals the one
	 * most deserving of attention wins — an unresolved ordering outranks a plain stale drop.
	 */
	private static String strongest(final String current, final String candidate)
	{
		if (current == null)
		{
			return candidate;
		}
		return OUTCOME_PRECEDENCE.indexOf(candidate) > OUTCOME_PRECEDENCE.indexOf(current) ? candidate : current;
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

	private static String describe(final Exception e)
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

	public void setRetryPolicy(final BillingRetryPolicy retryPolicy)
	{
		this.retryPolicy = retryPolicy;
	}

	public void setClock(final Clock clock)
	{
		this.clock = clock;
	}
}
