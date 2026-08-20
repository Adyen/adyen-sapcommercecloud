package com.adyen.commerce.connector.reconciliation.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import com.adyen.commerce.connector.dto.BillingSubscriptionRef;
import com.adyen.commerce.connector.dto.NormalizedSubscription;
import com.adyen.commerce.connector.exception.BillingException;
import com.adyen.commerce.connector.exception.PreconditionFailedException;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;
import com.adyen.commerce.connector.reconciliation.SubscriptionReconciliationService;
import com.adyen.commerce.connector.registry.SubscriptionBillingConnectorRegistry;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;

import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.util.Config;

public class DefaultSubscriptionReconciliationService implements SubscriptionReconciliationService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultSubscriptionReconciliationService.class);

	private SubscriptionBillingConnectorRegistry connectorRegistry;
	private ModelService modelService;
	private Clock clock = Clock.systemUTC();
	private boolean explicitRowLockingSupported = !Config.isHSQLDBUsed();

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
		final NormalizedSubscription snapshot = connector.fetchSubscription(ref);
		validateSnapshot(ref, snapshot);

		// The remote read happens before the row lock is taken, so no other writer is blocked for the
		// duration of the call. It is not outside the transaction, though: @Transactional covers the whole
		// method, so the round trip runs inside it and holds a database connection while it waits. Narrowing
		// it to the write half would take a separate transactional collaborator — an inner call on `this`
		// bypasses the Spring proxy and would silently do nothing.
		//
		// Once the response is available, refresh under the lock and compare the platform's own update
		// timestamp so a slow, older response cannot overwrite a newer reconciliation that completed first.
		if (subscription.getPk() != null)
		{
			// SAP Commerce's bundled HSQLDB explicitly rejects ModelService.lock(). HSQLDB is a
			// single-node development database, so retain the refresh and timestamp guard there but
			// reserve the real row lock for databases that support it.
			if (explicitRowLockingSupported)
			{
				modelService.lock(subscription.getPk());
			}
			modelService.refresh(subscription);
		}
		if (isOlderThanStoredSnapshot(subscription, snapshot))
		{
			LOG.info("Ignoring stale {} subscription snapshot for {} (platform updated {}, local snapshot {})",
					ref.platform(), ref.externalId(), snapshot.platformUpdatedAt(), subscription.getPlatformUpdatedAt());
			touchReconciliation(subscription, clock.instant());
			modelService.save(subscription);
			return snapshot;
		}

		apply(subscription, snapshot, clock.instant());
		modelService.save(subscription);
		return snapshot;
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
		model.setCancelAtPeriodEnd(Boolean.valueOf(snapshot.cancelAtPeriodEnd()));
		model.setPlatformUpdatedAt(toDate(snapshot.platformUpdatedAt()));
		touchReconciliation(model, reconciledAt);
	}

	/**
	 * Records that the platform was read, whether or not the answer changed anything. Both timestamps move
	 * together on purpose: lastReconciledAt is the audit trail of platform reads and lastSyncedAt is what
	 * takes the reference back out of the staleness sweep, and a read that is not credited to the sweep
	 * leaves the reference permanently due.
	 */
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

	void setClock(final Clock clock)
	{
		this.clock = clock;
	}

	void setExplicitRowLockingSupported(final boolean explicitRowLockingSupported)
	{
		this.explicitRowLockingSupported = explicitRowLockingSupported;
	}
}
