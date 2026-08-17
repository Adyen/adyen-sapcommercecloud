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
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;
import com.adyen.commerce.connector.model.BillingWebhookEventApplicationModel;
import com.adyen.commerce.connector.model.BillingWebhookEventModel;
import com.adyen.commerce.connector.reconciliation.SubscriptionReconciliationService;
import com.adyen.commerce.connector.registry.SubscriptionBillingConnectorRegistry;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;
import com.adyen.commerce.connector.webhook.SubscriptionBillingWebhookDispatcher;

import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;

/**
 * Verifies and deduplicates an inbound webhook, resolves the subscriptions it concerns and then reads
 * each subscription's current authoritative state from the billing platform.
 *
 * <p>The webhook is deliberately never projected directly onto a local status. A payment failure is a
 * transaction fact, an invoice past-due event is an invoice fact, and even a subscription-created event
 * can describe a future-dated subscription. The event therefore says only "this resource may have
 * changed"; {@link SubscriptionReconciliationService} decides the resulting local state from a live
 * platform snapshot.</p>
 *
 * <p>Delivery ordering is not a state rule. An old cancellation arriving after a reactivation still
 * triggers a read of the current subscription, which converges to the reactivated state. The platform
 * event id remains the deduplication key, while {@code BillingWebhookEventApplication} records the
 * independent result for every subscription behind a multi-subscription invoice.</p>
 */
public class DefaultSubscriptionBillingWebhookDispatcher implements SubscriptionBillingWebhookDispatcher
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultSubscriptionBillingWebhookDispatcher.class);

	protected static final String PROCESSING_RECEIVED = "RECEIVED";
	protected static final String PROCESSING_RECONCILED = "RECONCILED";
	protected static final String PROCESSING_SKIPPED_NO_SUBSCRIPTION = "SKIPPED_NO_SUBSCRIPTION";
	protected static final String PROCESSING_SKIPPED_UNSUPPORTED = "SKIPPED_UNSUPPORTED";
	protected static final String PROCESSING_SKIPPED_UNKNOWN_SUBSCRIPTION = "SKIPPED_UNKNOWN_SUBSCRIPTION";
	protected static final String PROCESSING_RETRYABLE_UNKNOWN_SUBSCRIPTION = "RETRYABLE_UNKNOWN_SUBSCRIPTION";
	protected static final String PROCESSING_FAILED = "FAILED";

	private static final Set<String> TERMINAL_OUTCOMES = Set.of(PROCESSING_RECONCILED,
			PROCESSING_SKIPPED_NO_SUBSCRIPTION, PROCESSING_SKIPPED_UNSUPPORTED);

	private SubscriptionBillingConnectorRegistry connectorRegistry;
	private FlexibleSearchService flexibleSearchService;
	private ModelService modelService;
	private Clock clock = Clock.systemUTC();
	private SubscriptionReconciliationService reconciliationService;

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

		final BillingWebhookEventModel record = alreadySeen.orElseGet(() -> newEventRecord(event, dedupKey));
		record.setAttemptCount(attemptCount(record) + 1);
		record.setProcessingStatus(PROCESSING_RECEIVED);
		record.setLastError(null);
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
			LOG.error("Failed to reconcile {} event '{}' on platform {}", event.type(), dedupKey, event.platform(), e);
			record.setProcessingStatus(PROCESSING_FAILED);
			record.setLastError(describe(e));
			modelService.save(record);
			throw e;
		}
	}

	/** Claims the platform event id before any remote lookup or local subscription update. */
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

	protected void handleUnknownSubscription(final NormalizedBillingEvent event,
			final BillingWebhookEventApplicationModel application, final String subscriptionId)
			throws RetryableBillingException
	{
		if (isDirectSubscriptionEvent(event))
		{
			application.setProcessingStatus(PROCESSING_RETRYABLE_UNKNOWN_SUBSCRIPTION);
			application.setLastError("Local subscription reference does not exist yet");
			modelService.save(application);
			throw new RetryableBillingException("Subscription " + subscriptionId
					+ " is not available locally yet; retrying protects the create/webhook race");
		}

		LOG.info("Ignoring {} event for external subscription {} that is not managed locally",
				event.type(), subscriptionId);
		application.setProcessingStatus(PROCESSING_SKIPPED_UNKNOWN_SUBSCRIPTION);
		application.setLastError(null);
		modelService.save(application);
	}

	protected boolean isDirectSubscriptionEvent(final NormalizedBillingEvent event)
	{
		if (event.externalSubscriptionId() == null || event.externalSubscriptionId().isBlank())
		{
			return false;
		}
		final String objectType = event.attributes().get("objectType");
		return objectType == null || "subscription".equals(objectType);
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

	/** Relevant projection fields before a reconciliation, used to version real state changes only. */
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

	public void setClock(final Clock clock)
	{
		this.clock = clock;
	}

	public void setReconciliationService(final SubscriptionReconciliationService reconciliationService)
	{
		this.reconciliationService = reconciliationService;
	}
}
