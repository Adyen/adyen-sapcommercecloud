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
import com.adyen.v6.enums.AdyenSubscriptionPlatform;
import com.adyen.v6.model.RecurlyConfigModel;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;

@UnitTest
public class DefaultRecurlyConfigServiceTest
{
    @Mock
    private ConfigurationService configurationService;
    @Mock
    private Configuration configuration;
    @Mock
    private BaseStoreService baseStoreService;
    @Mock
    private BaseStoreModel baseStore;
    @Mock
    private RecurlyConfigModel recurlyConfig;

    private DefaultRecurlyConfigService service;

    @Before
    public void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(configurationService.getConfiguration()).thenReturn(configuration);
        when(baseStoreService.getCurrentBaseStore()).thenReturn(baseStore);
        when(baseStore.getAdyenSubscriptionPlatform()).thenReturn(AdyenSubscriptionPlatform.RECURLY);
        when(baseStore.getRecurlyConfig()).thenReturn(recurlyConfig);
        service = new DefaultRecurlyConfigService(configurationService, baseStoreService);
    }

    @Test
    public void trimsTrailingSlashFromBaseUrl() throws Exception
    {
        when(recurlyConfig.getSubscriptionSiteId()).thenReturn("https://v3.recurly.com/");

        assertEquals("https://v3.recurly.com", service.getApiBaseUrl());
    }

    @Test
    public void rejectsBaseUrlWithoutScheme()
    {
        when(recurlyConfig.getSubscriptionSiteId()).thenReturn("v3.recurly.com");

        assertThrows(ConnectorNotConfiguredException.class, service::getApiBaseUrl);
    }

    @Test
    public void rejectsMissingRecurlyConfiguration()
    {
        when(baseStore.getRecurlyConfig()).thenReturn(null);

        assertThrows(ConnectorNotConfiguredException.class, service::getApiKey);
    }

    @Test
    public void rejectsBlankRequiredConfiguration()
    {
        when(recurlyConfig.getSubscriptionApiKey()).thenReturn("   ");

        assertThrows(ConnectorNotConfiguredException.class, service::getApiKey);
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
    public void readsFeatureFlagsFromRecurlyConfiguration() throws Exception
    {
        when(recurlyConfig.getExternalNtidFeatureEnabled()).thenReturn(true);
        when(recurlyConfig.getWalletEnabled()).thenReturn(false);

        assertTrue(service.isExternalNtidFeatureEnabled());
        assertFalse(service.isWalletEnabled());
    }
}
