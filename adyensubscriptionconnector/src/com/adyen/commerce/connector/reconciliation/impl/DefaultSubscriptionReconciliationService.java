package com.adyen.commerce.connector.reconciliation.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import com.adyen.commerce.connector.context.SubscriptionStoreContext;
import com.adyen.commerce.connector.dto.BillingSubscriptionRef;
import com.adyen.commerce.connector.dto.NormalizedSubscription;
import com.adyen.commerce.connector.dto.NormalizedSubscriptionStatus;
import com.adyen.commerce.connector.exception.BillingException;
import com.adyen.commerce.connector.exception.PreconditionFailedException;
import com.adyen.commerce.connector.exception.SubscriptionNotFoundException;
import com.adyen.commerce.connector.log.ConnectorLogEvent;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;
import com.adyen.commerce.connector.reconciliation.SubscriptionReconciliationService;
import com.adyen.commerce.connector.registry.SubscriptionBillingConnectorRegistry;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;

import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.util.Config;

/** Overwrites the local projection of a subscription with the platform's own state. */
public class DefaultSubscriptionReconciliationService implements SubscriptionReconciliationService
{
	static final String NOT_FOUND_THRESHOLD = "adyen.subscription.reconciliation.notFoundThreshold";
	static final int DEFAULT_NOT_FOUND_THRESHOLD = 3;

	private static final Logger LOG = LoggerFactory.getLogger(DefaultSubscriptionReconciliationService.class);
	private static final Set<String> TERMINAL_STATUSES = Set.of(
			NormalizedSubscriptionStatus.CANCELLED.name(),
			NormalizedSubscriptionStatus.FAILED.name(),
			NormalizedSubscriptionStatus.EXPIRED.name());
	private static final String EVENT_NOT_FOUND = "subscription_reconcile_not_found";

	private SubscriptionBillingConnectorRegistry connectorRegistry;
	private ModelService modelService;
	private ConfigurationService configurationService;
	private SubscriptionStoreContext storeContext;
	private Clock clock = Clock.systemUTC();
	private Boolean explicitRowLockingSupported;

	@Override
	@Transactional
	public NormalizedSubscription reconcile(final BillingSubscriptionRefModel subscription) throws BillingException
	{
		if (subscription == null)
		{
			throw new PreconditionFailedException("Cannot reconcile a null subscription reference");
		}
		final BillingSubscriptionRef ref = new BillingSubscriptionRef(subscription.getPlatform(),
				subscription.getExternalSubscriptionId());
		final SubscriptionBillingConnector connector = connectorRegistry.getConnector(ref.platform());
		final NormalizedSubscription snapshot;
		final Instant fetchStartedAt = clock.instant();
		try
		{
			snapshot = storeContext.callInStoreOf(subscription, store -> connector.fetchSubscription(ref));
		}
		catch (final SubscriptionNotFoundException notFound)
		{
			try
			{
				lockAndRefresh(subscription);
				if (!wasReadSuccessfullyMeanwhile(subscription, fetchStartedAt))
				{
					recordNotFound(subscription, clock.instant());
					modelService.save(subscription);
				}
			}
			catch (final RuntimeException failure)
			{
				failure.addSuppressed(notFound);
				throw failure;
			}
			// A checked exception: @Transactional commits the recorded miss.
			throw notFound;
		}
		validateSnapshot(ref, snapshot);

		lockAndRefresh(subscription);
		if (isOlderThanStoredSnapshot(subscription, snapshot))
		{
			LOG.info("Ignoring stale {} subscription snapshot for {} (platform updated {}, local snapshot {})",
					ref.platform(), ref.externalId(), snapshot.platformUpdatedAt(), subscription.getPlatformUpdatedAt());
			touchReconciliation(subscription, clock.instant());
			clearNotFound(subscription);
			modelService.save(subscription);
			return snapshot;
		}

		apply(subscription, snapshot, clock.instant());
		modelService.save(subscription);
		return snapshot;
	}

	/**
	 * Read before the row lock so no writer waits on the remote call; the refresh under the lock and the
	 * platform timestamp keep an older response from overwriting a newer one.
	 */
	protected void lockAndRefresh(final BillingSubscriptionRefModel subscription)
	{
		if (subscription.getPk() != null)
		{
			// HSQLDB rejects ModelService.lock(); the refresh and timestamp guard stand in for it there.
			if (isExplicitRowLockingSupported())
			{
				modelService.lock(subscription.getPk());
			}
			modelService.refresh(subscription);
		}
	}

	/** Resolved on first use, so the platform config is only touched when a persisted row is actually locked. */
	protected boolean isExplicitRowLockingSupported()
	{
		if (explicitRowLockingSupported == null)
		{
			explicitRowLockingSupported = !Config.isHSQLDBUsed();
		}
		return explicitRowLockingSupported;
	}

	protected boolean isOlderThanStoredSnapshot(final BillingSubscriptionRefModel model,
	                                           final NormalizedSubscription snapshot)
	{
		return snapshot.platformUpdatedAt() != null
				&& model.getPlatformUpdatedAt() != null
				&& snapshot.platformUpdatedAt().isBefore(model.getPlatformUpdatedAt().toInstant());
	}

	protected void validateSnapshot(final BillingSubscriptionRef requested,
	                                final NormalizedSubscription snapshot)
			throws PreconditionFailedException
	{
		if (snapshot == null)
		{
			throw new PreconditionFailedException("Connector returned no subscription snapshot");
		}

		if (snapshot.subscription().platform() != requested.platform())
		{
			throw new PreconditionFailedException(
					"Connector returned a " + snapshot.subscription().platform()
							+ " snapshot for a " + requested.platform()
							+ " subscription");
		}

		if (!requested.externalId().equals(snapshot.subscription().externalId()))
		{
			throw new PreconditionFailedException(
					"Connector returned subscription "
							+ snapshot.subscription().externalId()
							+ " while reconciling "
							+ requested.externalId());
		}
	}

	protected void apply(final BillingSubscriptionRefModel model,
	                     final NormalizedSubscription snapshot,
	                     final Instant reconciledAt)
	{
		model.setExternalSubscriptionId(snapshot.subscription().externalId());
		model.setStatus(snapshot.status().name());
		model.setPlanCode(snapshot.planId());
		model.setQuantity(snapshot.quantity());
		model.setCurrentPeriodStart(toDate(snapshot.currentPeriodStart()));
		model.setCurrentPeriodEnd(toDate(snapshot.currentPeriodEnd()));
		model.setCancelAtPeriodEnd(snapshot.cancelAtPeriodEnd());
		model.setPlatformUpdatedAt(toDate(snapshot.platformUpdatedAt()));
		touchReconciliation(model, reconciledAt);
		clearNotFound(model);
	}

	/** Another reader refreshed a reference with no recorded miss while this fetch was in flight: the 404 is stale. */
	protected boolean wasReadSuccessfullyMeanwhile(final BillingSubscriptionRefModel model, final Instant fetchStartedAt)
	{
		return model.getNotFoundSince() == null
				&& model.getLastReconciledAt() != null
				&& model.getLastReconciledAt().toInstant().isAfter(fetchStartedAt);
	}

	/**
	 * Counts a 404 and, once the threshold is reached, marks the reference UNKNOWN. Nothing else is touched: the
	 * last known plan and period stay as they were. The read still moves lastSyncedAt, so the reference falls
	 * back to the normal staleness rhythm.
	 */
	protected void recordNotFound(final BillingSubscriptionRefModel model, final Instant reconciledAt)
	{
		final String previousStatus = model.getStatus();
		final int previous = model.getNotFoundCount() == null ? 0 : model.getNotFoundCount();
		final int count = previous + 1;
		final int threshold = getNotFoundThreshold();
		model.setNotFoundCount(count);
		if (model.getNotFoundSince() == null)
		{
			model.setNotFoundSince(Date.from(reconciledAt));
		}
		touchReconciliation(model, reconciledAt);
		final boolean markedUnknown = count >= threshold && isMarkableAsUnknown(previousStatus);
		if (markedUnknown)
		{
			model.setStatus(NormalizedSubscriptionStatus.UNKNOWN.name());
		}

		final ConnectorLogEvent event = ConnectorLogEvent.of(EVENT_NOT_FOUND)
				.platform(model.getPlatform())
				.field("external_id", model.getExternalSubscriptionId())
				.field("not_found_count", count)
				.field("threshold", threshold);
		if (markedUnknown)
		{
			event.field("marked_unknown", true).warn(LOG);
		}
		else if (count == 1)
		{
			event.warn(LOG);
		}
		else
		{
			event.debug(LOG);
		}
	}

	/** A subscription already ended on the platform's own say-so keeps that status; only the miss is recorded. */
	protected boolean isMarkableAsUnknown(final String status)
	{
		return !NormalizedSubscriptionStatus.UNKNOWN.name().equals(status)
				&& (status == null || !TERMINAL_STATUSES.contains(status));
	}

	/** Any successful read proves the platform knows the reference. */
	protected void clearNotFound(final BillingSubscriptionRefModel model)
	{
		model.setNotFoundCount(null);
		model.setNotFoundSince(null);
	}

	protected int getNotFoundThreshold()
	{
		return Math.max(1, configurationService.getConfiguration().getInt(NOT_FOUND_THRESHOLD,
				DEFAULT_NOT_FOUND_THRESHOLD));
	}

	/** Records the platform read; lastSyncedAt is what takes the reference out of the staleness sweep. */
	protected void touchReconciliation(final BillingSubscriptionRefModel model, final Instant reconciledAt)
	{
		model.setLastReconciledAt(Date.from(reconciledAt));
		model.setLastSyncedAt(Date.from(reconciledAt));
	}

	protected Date toDate(final Instant value)
	{
		return value == null ? null : Date.from(value);
	}

	public void setConnectorRegistry(final SubscriptionBillingConnectorRegistry connectorRegistry)
	{
		this.connectorRegistry = connectorRegistry;
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}

	public void setConfigurationService(final ConfigurationService configurationService)
	{
		this.configurationService = configurationService;
	}

	public void setStoreContext(final SubscriptionStoreContext storeContext)
	{
		this.storeContext = storeContext;
	}

	void setClock(final Clock clock)
	{
		this.clock = clock;
	}

	void setExplicitRowLockingSupported(final boolean explicitRowLockingSupported)
	{
		this.explicitRowLockingSupported = explicitRowLockingSupported;
	}
}
