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

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.ConnectorNotConfiguredException;
import com.adyen.commerce.connector.registry.SubscriptionBillingConnectorRegistry;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;

import de.hybris.platform.store.BaseStoreModel;

/**
 * Default registry. Connectors are auto-discovered from the Spring application context (every
 * {@link SubscriptionBillingConnector} bean an adapter extension declares); an explicit list can also
 * be injected, which takes precedence and is used by unit tests.
 */
public class DefaultSubscriptionBillingConnectorRegistry
		implements SubscriptionBillingConnectorRegistry, ApplicationContextAware
{
	private List<SubscriptionBillingConnector> connectors = new ArrayList<>();
	private boolean connectorsInjected = false;
	private volatile List<SubscriptionBillingConnector> discovered;
	private ApplicationContext applicationContext;

	@Override
	public Optional<SubscriptionBillingConnector> findConnector(final BillingPlatform platform)
	{
		if (platform == null)
		{
			return Optional.empty();
		}
		return effectiveConnectors().stream().filter(c -> platform.equals(c.platform())).findFirst();
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
		return effectiveConnectors();
	}

	/**
	 * An explicitly injected list wins (used by tests); otherwise every {@link SubscriptionBillingConnector}
	 * bean in the application context is discovered.
	 */
	protected List<SubscriptionBillingConnector> effectiveConnectors()
	{
		// An explicitly injected list wins even when empty (intentionally disabling all connectors);
		// only the never-injected default auto-discovers from the context (discovered once and cached).
		if (connectorsInjected || applicationContext == null)
		{
			return connectors;
		}
		List<SubscriptionBillingConnector> snapshot = discovered;
		if (snapshot == null)
		{
			synchronized (this)
			{
				snapshot = discovered;
				if (snapshot == null)
				{
					snapshot = List.copyOf(applicationContext.getBeansOfType(SubscriptionBillingConnector.class).values());
					discovered = snapshot;
				}
			}
		}
		return snapshot;
	}

	public void setConnectors(final List<SubscriptionBillingConnector> connectors)
	{
		this.connectors = connectors != null ? connectors : new ArrayList<>();
		this.connectorsInjected = true;
	}

	@Override
	public void setApplicationContext(final ApplicationContext applicationContext) throws BeansException
	{
		this.applicationContext = applicationContext;
	}
}
