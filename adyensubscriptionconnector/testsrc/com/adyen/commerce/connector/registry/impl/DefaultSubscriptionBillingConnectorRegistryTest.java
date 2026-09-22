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

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationContext;

import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.ConnectorNotConfiguredException;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.store.BaseStoreModel;

/**
 * Unit test for {@link DefaultSubscriptionBillingConnectorRegistry} — resolution by platform and per
 * BaseStore.
 */
@UnitTest
public class DefaultSubscriptionBillingConnectorRegistryTest
{
	@Mock
	private SubscriptionBillingConnector chargebee;
	@Mock
	private BaseStoreModel store;

	private DefaultSubscriptionBillingConnectorRegistry registry;

	@Before
	public void setUp()
	{
		MockitoAnnotations.openMocks(this);
		when(chargebee.platform()).thenReturn(BillingPlatform.CHARGEBEE);
		registry = new DefaultSubscriptionBillingConnectorRegistry();
		registry.setConnectors(List.of(chargebee));
	}

	@Test
	public void shouldResolveByPlatform() throws Exception
	{
		assertSame(chargebee, registry.getConnector(BillingPlatform.CHARGEBEE));
		assertTrue(registry.findConnector(BillingPlatform.ZUORA).isEmpty());
	}

	@Test
	public void shouldAutoDiscoverConnectorsFromApplicationContext() throws Exception
	{
		final ApplicationContext context = mock(ApplicationContext.class);
		when(context.getBeansOfType(SubscriptionBillingConnector.class)).thenReturn(Map.of("chargebee", chargebee));
		final DefaultSubscriptionBillingConnectorRegistry autoRegistry = new DefaultSubscriptionBillingConnectorRegistry();
		autoRegistry.setApplicationContext(context);

		assertSame(chargebee, autoRegistry.getConnector(BillingPlatform.CHARGEBEE));
	}

	@Test
	public void injectedEmptyListDisablesAutoDiscovery()
	{
		final ApplicationContext context = mock(ApplicationContext.class);
		final DefaultSubscriptionBillingConnectorRegistry emptyRegistry = new DefaultSubscriptionBillingConnectorRegistry();
		emptyRegistry.setApplicationContext(context);
		emptyRegistry.setConnectors(List.of());

		assertThrows(ConnectorNotConfiguredException.class, () -> emptyRegistry.getConnector(BillingPlatform.CHARGEBEE));
		verify(context, never()).getBeansOfType(SubscriptionBillingConnector.class);
	}

	@Test
	public void shouldThrowForUnregisteredPlatform()
	{
		assertThrows(ConnectorNotConfiguredException.class, () -> registry.getConnector(BillingPlatform.ZUORA));
	}

	@Test
	public void shouldResolveActiveConnectorFromStore() throws Exception
	{
		when(store.getActiveBillingPlatform()).thenReturn(BillingPlatform.CHARGEBEE);
		assertSame(chargebee, registry.getActiveConnector(store));
	}

	@Test
	public void shouldThrowWhenStoreHasNoActivePlatform()
	{
		when(store.getActiveBillingPlatform()).thenReturn(null);
		assertThrows(ConnectorNotConfiguredException.class, () -> registry.getActiveConnector(store));
	}
}
