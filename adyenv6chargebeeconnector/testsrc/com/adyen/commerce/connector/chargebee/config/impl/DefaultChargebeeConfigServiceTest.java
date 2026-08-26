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
package com.adyen.commerce.connector.chargebee.config.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import org.apache.commons.configuration2.Configuration;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.ConnectorNotConfiguredException;
import com.adyen.v6.model.ChargebeeConfigModel;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;

/**
 * Unit test for {@link DefaultChargebeeConfigService}: required vs optional attributes read off the
 * current base store's Chargebee configuration, and the derived API base URL.
 */
@UnitTest
public class DefaultChargebeeConfigServiceTest
{
	@Mock
	private BaseStoreService baseStoreService;
	@Mock
	private BaseStoreModel baseStore;
	@Mock
	private ChargebeeConfigModel chargebeeConfig;
	@Mock
	private ConfigurationService configurationService;
	@Mock
	private Configuration configuration;

	private DefaultChargebeeConfigService service;

	@Before
	public void setUp()
	{
		MockitoAnnotations.openMocks(this);
		when(baseStoreService.getCurrentBaseStore()).thenReturn(baseStore);
		when(baseStore.getChargebeeConfig()).thenReturn(chargebeeConfig);
		when(configurationService.getConfiguration()).thenReturn(configuration);
		service = new DefaultChargebeeConfigService();
		service.setBaseStoreService(baseStoreService);
		service.setConfigurationService(configurationService);
	}

	/**
	 * Transport tuning is per installation, so it stays in properties rather than on the base store.
	 */
	@Test
	public void transportTuningIsReadFromProperties()
	{
		when(configuration.getInt("chargebee.http.connectTimeoutMillis", 5000)).thenReturn(1234);
		when(configuration.getInt("chargebee.http.responseTimeoutMillis", 5000)).thenReturn(4321);
		when(configuration.getInt("chargebee.http.connectionRequestTimeoutMillis", 5000)).thenReturn(999);
		when(configuration.getInt("chargebee.http.maxConnections", 20)).thenReturn(50);

		assertEquals(1234, service.getConnectTimeoutMillis());
		assertEquals(4321, service.getResponseTimeoutMillis());
		assertEquals(999, service.getConnectionRequestTimeoutMillis());
		assertEquals(50, service.getMaxConnections());
	}

	/**
	 * A non-positive override would restore the unbounded wait this configuration exists to remove, so
	 * it must fall back rather than be passed through.
	 */
	@Test
	public void nonPositiveTransportOverridesFallBackToTheDefault()
	{
		when(configuration.getInt("chargebee.http.connectTimeoutMillis", 5000)).thenReturn(0);
		when(configuration.getInt("chargebee.http.responseTimeoutMillis", 5000)).thenReturn(-1);
		when(configuration.getInt("chargebee.http.connectionRequestTimeoutMillis", 5000)).thenReturn(-100);
		when(configuration.getInt("chargebee.http.maxConnections", 20)).thenReturn(0);

		assertEquals(5000, service.getConnectTimeoutMillis());
		assertEquals(5000, service.getResponseTimeoutMillis());
		assertEquals(5000, service.getConnectionRequestTimeoutMillis());
		assertEquals(20, service.getMaxConnections());
	}

	@Test
	public void buildsApiBaseUrlFromSite() throws Exception
	{
		when(chargebeeConfig.getSubscriptionSiteId()).thenReturn("acme");

		assertEquals("https://acme.chargebee.com/api/v2", service.getApiBaseUrl());
	}

	@Test
	public void missingApiKeyThrowsConnectorNotConfigured()
	{
		when(chargebeeConfig.getSubscriptionApiKey()).thenReturn(null);

		assertThrows(ConnectorNotConfiguredException.class, service::getApiKey);
	}

	@Test
	public void blankSiteIsTreatedAsUnset()
	{
		when(chargebeeConfig.getSubscriptionSiteId()).thenReturn("   ");

		assertThrows(ConnectorNotConfiguredException.class, service::getSiteName);
	}

	@Test
	public void requiredAttributesAreTrimmed() throws Exception
	{
		when(chargebeeConfig.getSubscriptionApiKey()).thenReturn("  cb-key  ");

		assertEquals("cb-key", service.getApiKey());
	}

	@Test
	public void missingChargebeeConfigThrowsConnectorNotConfigured()
	{
		when(baseStore.getChargebeeConfig()).thenReturn(null);

		assertThrows(ConnectorNotConfiguredException.class, service::getApiKey);
	}

	@Test
	public void missingBaseStoreThrowsConnectorNotConfigured()
	{
		when(baseStoreService.getCurrentBaseStore()).thenReturn(null);

		assertThrows(ConnectorNotConfiguredException.class, service::getApiKey);
	}

	@Test
	public void gatewayAccountIdReadFromChargebeeConfig()
	{
		when(chargebeeConfig.getSubscriptionGatewayAccountId()).thenReturn("gw_adyen");

		assertEquals("gw_adyen", service.getGatewayAccountId());
	}

	/**
	 * The gateway-binding guard compares this against the store's own Adyen merchant account, so it has to come from
	 * the Chargebee configuration. Reading it off the store would make the comparison a tautology — the
	 * store value is stubbed differently here on purpose to catch that regression.
	 */
	@Test
	public void merchantAccountReadFromChargebeeConfigNotFromBaseStore()
	{
		when(chargebeeConfig.getAdyenGatewayMerchantAccount()).thenReturn("AdyenGatewayECOM");
		when(baseStore.getAdyenMerchantAccount()).thenReturn("AdyenStoreECOM");

		assertEquals("AdyenGatewayECOM", service.getConfiguredAdyenMerchantAccount());
	}

	/**
	 * Mirrors the Recurly side: this service deliberately does not gate on the store's
	 * activeBillingPlatform. Cancellation routes on the subscription's own platform, so a store that has
	 * migrated to another platform must still reach its Chargebee credentials to cancel what it created
	 * there. Re-adding a gate here would fail this test.
	 */
	@Test
	public void credentialsStayReadableForAStoreThatHasMovedToAnotherPlatform() throws Exception
	{
		when(chargebeeConfig.getSubscriptionApiKey()).thenReturn("cb-key");
		when(baseStore.getActiveBillingPlatform()).thenReturn(BillingPlatform.RECURLY);

		assertEquals("cb-key", service.getApiKey());
	}

	@Test
	public void blankMerchantAccountIsTreatedAsUnset()
	{
		when(chargebeeConfig.getAdyenGatewayMerchantAccount()).thenReturn("   ");

		assertNull(service.getConfiguredAdyenMerchantAccount());
	}

	@Test
	public void webhookCredentialsReadFromChargebeeConfig()
	{
		when(chargebeeConfig.getChargebeeWebhookUsername()).thenReturn("cb-user");
		when(chargebeeConfig.getChargebeeWebhookPassword()).thenReturn("cb-pass");

		assertEquals("cb-user", service.getWebhookUsername());
		assertEquals("cb-pass", service.getWebhookPassword());
	}

	/**
	 * The nullable getters cannot throw, so an absent configuration has to read as "unset" — the
	 * webhook auth check fails closed on that.
	 */
	@Test
	public void optionalGettersAreNullWhenChargebeeConfigMissing()
	{
		when(baseStore.getChargebeeConfig()).thenReturn(null);

		assertNull(service.getGatewayAccountId());
		assertNull(service.getWebhookUsername());
		assertNull(service.getWebhookPassword());
	}

	@Test
	public void optionalGettersAreNullWhenBaseStoreMissing()
	{
		when(baseStoreService.getCurrentBaseStore()).thenReturn(null);

		assertNull(service.getGatewayAccountId());
		assertNull(service.getConfiguredAdyenMerchantAccount());
	}

	@Test
	public void blankOptionalAttributesAreTreatedAsUnset()
	{
		when(chargebeeConfig.getSubscriptionGatewayAccountId()).thenReturn("  ");
		when(chargebeeConfig.getChargebeeWebhookUsername()).thenReturn("");
		when(chargebeeConfig.getChargebeeWebhookPassword()).thenReturn("\t");

		assertNull(service.getGatewayAccountId());
		assertNull(service.getWebhookUsername());
		assertNull(service.getWebhookPassword());
	}
}
