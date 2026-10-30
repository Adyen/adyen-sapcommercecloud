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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.registry.SubscriptionBillingConnectorRegistry;

import de.hybris.bootstrap.annotations.UnitTest;

/**
 * Unit test for {@link ConnectorBillingPlatformAvailabilityService}.
 */
@UnitTest
public class ConnectorBillingPlatformAvailabilityServiceTest
{
	private static final BillingPlatform RECURLY = BillingPlatform.valueOf("RECURLY");
	private static final BillingPlatform CHARGEBEE = BillingPlatform.valueOf("CHARGEBEE");

	@Mock
	private SubscriptionBillingConnectorRegistry connectorRegistry;

	private ConnectorBillingPlatformAvailabilityService availabilityService;

	@Before
	public void setUp()
	{
		MockitoAnnotations.openMocks(this);
		availabilityService = new ConnectorBillingPlatformAvailabilityService();
		availabilityService.setConnectorRegistry(connectorRegistry);
	}

	@Test
	public void reportsTheCodesOfRegisteredPlatforms()
	{
		when(connectorRegistry.getAvailablePlatforms()).thenReturn(new LinkedHashSet<>(List.of(RECURLY, CHARGEBEE)));

		assertEquals(Set.of("RECURLY", "CHARGEBEE"), availabilityService.getAvailablePlatformCodes());
		assertTrue(availabilityService.isAvailable("RECURLY"));
		assertFalse(availabilityService.isAvailable("UNSUPPORTED_PLATFORM"));
	}

	@Test
	public void reportsNothingWithoutConnectors()
	{
		when(connectorRegistry.getAvailablePlatforms()).thenReturn(Set.of());

		assertTrue(availabilityService.getAvailablePlatformCodes().isEmpty());
		assertFalse(availabilityService.isAvailable("RECURLY"));
	}

	@Test
	public void treatsABlankCodeAsUnavailable()
	{
		when(connectorRegistry.getAvailablePlatforms()).thenReturn(Set.of(RECURLY));

		assertFalse(availabilityService.isAvailable(null));
		assertFalse(availabilityService.isAvailable(" "));
	}
}
