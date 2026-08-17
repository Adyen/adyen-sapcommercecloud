package com.adyen.commerce.connector.reconciliation.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;

import com.adyen.commerce.connector.dto.BillingSubscriptionRef;
import com.adyen.commerce.connector.dto.NormalizedSubscription;
import com.adyen.commerce.connector.exception.BillingException;
import com.adyen.commerce.connector.exception.PreconditionFailedException;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;
import com.adyen.commerce.connector.reconciliation.SubscriptionReconciliationService;
import com.adyen.commerce.connector.registry.SubscriptionBillingConnectorRegistry;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;

import de.hybris.platform.servicelayer.model.ModelService;

public class DefaultSubscriptionReconciliationService implements SubscriptionReconciliationService
{
	private SubscriptionBillingConnectorRegistry connectorRegistry;
	private ModelService modelService;
	private Clock clock = Clock.systemUTC();

	@Override
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
		apply(subscription, snapshot, clock.instant());
		modelService.save(subscription);
		return snapshot;
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

		modelService.setAttributeValue(
				model,
				"cancelAtPeriodEnd",
				Boolean.valueOf(snapshot.cancelAtPeriodEnd()));

		modelService.setAttributeValue(
				model,
				"platformUpdatedAt",
				toDate(snapshot.platformUpdatedAt()));

		modelService.setAttributeValue(
				model,
				"lastReconciledAt",
				Date.from(reconciledAt));

		modelService.setAttributeValue(
				model,
				"lastSyncedAt",
				Date.from(reconciledAt));
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
}
