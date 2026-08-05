package com.adyen.commerce.connector.recurly.config.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import org.apache.commons.configuration2.Configuration;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adyen.commerce.connector.exception.ConnectorNotConfiguredException;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.servicelayer.config.ConfigurationService;

@UnitTest
public class DefaultRecurlyConfigServiceTest
{
    @Mock
    private ConfigurationService configurationService;
    @Mock
    private Configuration configuration;

    private DefaultRecurlyConfigService service;

    @Before
    public void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(configurationService.getConfiguration()).thenReturn(configuration);
        service = new DefaultRecurlyConfigService(configurationService);
    }

    @Test
    public void trimsTrailingSlashFromBaseUrl() throws Exception
    {
        when(configuration.getString("recurly.baseUrl", null)).thenReturn("https://v3.recurly.com/");
        assertEquals("https://v3.recurly.com", service.getApiBaseUrl());
    }

    @Test
    public void rejectsMissingRequiredConfiguration()
    {
        assertThrows(ConnectorNotConfiguredException.class, service::getApiKey);
        assertThrows(ConnectorNotConfiguredException.class, service::getGatewayCode);
        assertThrows(ConnectorNotConfiguredException.class, service::getWebhookSigningKey);
    }

    @Test
    public void usesSafeDefaults()
    {
        when(configuration.getString("recurly.apiVersion", null)).thenReturn(null);
        when(configuration.getInt("recurly.minimumStartDelaySeconds", 300)).thenReturn(0);
        when(configuration.getInt("recurly.http.connectTimeoutMillis", 5000)).thenReturn(-1);
        when(configuration.getInt("recurly.http.responseTimeoutMillis", 30000)).thenReturn(0);
        when(configuration.getInt("recurly.webhookToleranceSeconds", 300)).thenReturn(-1);

        assertEquals("v2021-02-25", service.getApiVersion());
        assertEquals(300, service.getMinimumStartDelaySeconds());
        assertEquals(5000, service.getConnectTimeoutMillis());
        assertEquals(30000, service.getResponseTimeoutMillis());
        assertEquals(300, service.getWebhookToleranceSeconds());
    }

    @Test
    public void featureConfirmationsDefaultToFalse()
    {
        when(configuration.getBoolean("recurly.externalNtidFeatureEnabled", false)).thenReturn(false);
        when(configuration.getBoolean("recurly.walletEnabled", false)).thenReturn(false);
        assertFalse(service.isExternalNtidFeatureEnabled());
        assertFalse(service.isWalletEnabled());
    }

    @Test
    public void readsConfirmedFeatures()
    {
        when(configuration.getBoolean("recurly.externalNtidFeatureEnabled", false)).thenReturn(true);
        when(configuration.getBoolean("recurly.walletEnabled", false)).thenReturn(true);
        assertTrue(service.isExternalNtidFeatureEnabled());
        assertTrue(service.isWalletEnabled());
    }
}
