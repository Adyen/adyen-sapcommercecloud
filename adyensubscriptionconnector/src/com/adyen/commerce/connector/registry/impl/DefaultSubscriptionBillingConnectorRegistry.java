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
package com.adyen.commerce.connector.registry.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.ConnectorNotConfiguredException;
import com.adyen.commerce.connector.registry.SubscriptionBillingConnectorRegistry;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;

import de.hybris.platform.store.BaseStoreModel;

/**
 * Default registry. Connectors are injected as a Spring list each adapter extension can merge into.
 */
public class DefaultSubscriptionBillingConnectorRegistry implements SubscriptionBillingConnectorRegistry
{
	private List<SubscriptionBillingConnector> connectors = new ArrayList<>();

	@Override
	public Optional<SubscriptionBillingConnector> findConnector(final BillingPlatform platform)
	{
		if (platform == null)
		{
			return Optional.empty();
		}
		return connectors.stream().filter(c -> platform.equals(c.platform())).findFirst();
	}

	@Override
	public SubscriptionBillingConnector getConnector(final BillingPlatform platform) throws ConnectorNotConfiguredException
	{
		return findConnector(platform).orElseThrow(() -> new ConnectorNotConfiguredException(
				"No subscription billing connector registered for platform " + platform));
	}

	@Override
	public SubscriptionBillingConnector getActiveConnector(final BaseStoreModel store) throws ConnectorNotConfiguredException
	{
		if (store == null)
		{
			throw new ConnectorNotConfiguredException("Cannot resolve a billing connector without a base store");
		}
		final BillingPlatform active = store.getActiveBillingPlatform();
		if (active == null)
		{
			throw new ConnectorNotConfiguredException(
					"No active billing platform configured on base store '" + store.getUid() + "'");
		}
		return getConnector(active);
	}

	@Override
	public List<SubscriptionBillingConnector> getConnectors()
	{
		return connectors;
	}

	public void setConnectors(final List<SubscriptionBillingConnector> connectors)
	{
		this.connectors = connectors != null ? connectors : new ArrayList<>();
	}
}
