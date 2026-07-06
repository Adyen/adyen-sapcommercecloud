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

import com.adyen.commerce.connector.exception.ConnectorNotConfiguredException;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.servicelayer.config.ConfigurationService;

/**
 * Unit test for {@link DefaultChargebeeConfigService}: required vs optional properties and the
 * derived API base URL.
 */
@UnitTest
public class DefaultChargebeeConfigServiceTest
{
	@Mock
	private ConfigurationService configurationService;
	@Mock
	private Configuration configuration;

	private DefaultChargebeeConfigService service;

	@Before
	public void setUp()
	{
		MockitoAnnotations.openMocks(this);
		service = new DefaultChargebeeConfigService();
		service.setConfigurationService(configurationService);
		when(configurationService.getConfiguration()).thenReturn(configuration);
	}

	@Test
	public void buildsApiBaseUrlFromSite() throws Exception
	{
		when(configuration.getString("chargebee.site", null)).thenReturn("acme");
		assertEquals("https://acme.chargebee.com/api/v2", service.getApiBaseUrl());
	}

	@Test
	public void missingApiKeyThrowsConnectorNotConfigured()
	{
		when(configuration.getString("chargebee.apiKey", null)).thenReturn(null);
		assertThrows(ConnectorNotConfiguredException.class, service::getApiKey);
	}

	@Test
	public void blankSiteIsTreatedAsUnset()
	{
		when(configuration.getString("chargebee.site", null)).thenReturn("   ");
		assertThrows(ConnectorNotConfiguredException.class, service::getSiteName);
	}

	@Test
	public void optionalMerchantAccountIsNullWhenUnset()
	{
		when(configuration.getString("chargebee.adyenMerchantAccount", null)).thenReturn(null);
		assertNull(service.getConfiguredAdyenMerchantAccount());
	}
}
