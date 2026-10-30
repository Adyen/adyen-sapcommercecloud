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

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.registry.SubscriptionBillingConnectorRegistry;
import com.adyen.commerce.services.BillingPlatformAvailabilityService;

/**
 * Answers the core's availability question from the connector registry: a platform is available exactly when
 * a connector for it is registered.
 */
public class ConnectorBillingPlatformAvailabilityService implements BillingPlatformAvailabilityService
{
	private SubscriptionBillingConnectorRegistry connectorRegistry;

	@Override
	public Set<String> getAvailablePlatformCodes()
	{
		final Set<String> codes = getConnectorRegistry().getAvailablePlatforms().stream().map(BillingPlatform::getCode)
				.collect(Collectors.toCollection(LinkedHashSet::new));
		return Collections.unmodifiableSet(codes);
	}

	@Override
	public boolean isAvailable(final String platformCode)
	{
		return StringUtils.isNotBlank(platformCode) && getAvailablePlatformCodes().contains(platformCode);
	}

	protected SubscriptionBillingConnectorRegistry getConnectorRegistry()
	{
		return connectorRegistry;
	}

	public void setConnectorRegistry(final SubscriptionBillingConnectorRegistry connectorRegistry)
	{
		this.connectorRegistry = connectorRegistry;
	}
}
