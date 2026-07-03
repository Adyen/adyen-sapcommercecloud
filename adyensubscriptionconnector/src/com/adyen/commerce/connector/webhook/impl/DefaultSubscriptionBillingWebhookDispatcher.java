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

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adyen.commerce.connector.dto.BillingEventType;
import com.adyen.commerce.connector.dto.NormalizedBillingEvent;
import com.adyen.commerce.connector.dto.RawWebhook;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.BillingException;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;
import com.adyen.commerce.connector.registry.SubscriptionBillingConnectorRegistry;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;
import com.adyen.commerce.connector.webhook.SubscriptionBillingWebhookDispatcher;

import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;

/**
 * Default dispatcher. Verifies/normalizes via the connector, then applies a minimal status
 * reconciliation to the local {@code BillingSubscriptionRef}. Full reconciliation / source-of-truth
 * handling (out-of-order, duplicate, replay) is design P4.1.
 */
public class DefaultSubscriptionBillingWebhookDispatcher implements SubscriptionBillingWebhookDispatcher
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultSubscriptionBillingWebhookDispatcher.class);

	private SubscriptionBillingConnectorRegistry connectorRegistry;
	private FlexibleSearchService flexibleSearchService;
	private ModelService modelService;

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
		if (event == null || event.externalSubscriptionId() == null)
		{
			return;
		}
		final Optional<BillingSubscriptionRefModel> ref = findByExternalId(event.platform(), event.externalSubscriptionId());
		if (ref.isEmpty())
		{
			LOG.warn("Received {} event for unknown subscription {} on platform {}", event.type(),
					event.externalSubscriptionId(), event.platform());
			return;
		}
		final String newStatus = mapStatus(event.type());
		if (newStatus != null)
		{
			final BillingSubscriptionRefModel model = ref.get();
			model.setStatus(newStatus);
			modelService.save(model);
		}
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
}
