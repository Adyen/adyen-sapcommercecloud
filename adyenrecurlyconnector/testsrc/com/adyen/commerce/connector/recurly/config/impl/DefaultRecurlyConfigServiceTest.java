package com.adyen.commerce.connector.recurly.config.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import org.apache.commons.configuration2.Configuration;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.ConnectorNotConfiguredException;
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

    /**
     * A store that has migrated to another platform still has to cancel the subscriptions it created on
     * Recurly, and cancellation routes on the subscription's own platform rather than on the store's
     * activeBillingPlatform. Gating this service on the active platform would strand exactly those.
     */
    @Test
    public void credentialsStayReadableForAStoreThatHasMovedToAnotherPlatform() throws Exception
    {
        when(recurlyConfig.getSubscriptionApiKey()).thenReturn("recurly-key");
        when(recurlyConfig.getWalletEnabled()).thenReturn(true);
        when(baseStore.getActiveBillingPlatform()).thenReturn(BillingPlatform.CHARGEBEE);

        assertEquals("recurly-key", service.getApiKey());
        assertTrue(service.isWalletEnabled());
    }

    @Test
    public void merchantAccountReadFromRecurlyConfigNotFromBaseStore()
    {
        when(recurlyConfig.getAdyenGatewayMerchantAccount()).thenReturn("AdyenGatewayECOM");
        when(baseStore.getAdyenMerchantAccount()).thenReturn("AdyenStoreECOM");

        assertEquals("AdyenGatewayECOM", service.getConfiguredAdyenMerchantAccount());
    }

    /**
     * The mode flags must not read as "off" when the store simply is not configured — that would run the
     * non-wallet / no-NTID flow against a site set up for the opposite, silently.
     */
    @Test
    public void modeFlagsThrowWhenRecurlyConfigMissing()
    {
        when(baseStore.getRecurlyConfig()).thenReturn(null);

        assertThrows(ConnectorNotConfiguredException.class, service::isWalletEnabled);
        assertThrows(ConnectorNotConfiguredException.class, service::isExternalNtidFeatureEnabled);
    }

    @Test
    public void modeFlagsReadFalseWhenTheStoredValueIsNull() throws Exception
    {
        when(recurlyConfig.getWalletEnabled()).thenReturn(null);
        when(recurlyConfig.getExternalNtidFeatureEnabled()).thenReturn(null);

        assertFalse(service.isWalletEnabled());
        assertFalse(service.isExternalNtidFeatureEnabled());
    }

    /**
     * The SPI forbids this one from throwing, so here "not configured" really is null. The connector's
     * own verifyMerchantAccount rejects a blank, which is what keeps that check fail-closed.
     */
    @Test
    public void merchantAccountIsNullRatherThanThrownWhenRecurlyConfigMissing()
    {
        when(baseStore.getRecurlyConfig()).thenReturn(null);

        assertNull(service.getConfiguredAdyenMerchantAccount());
    }

    @Test
    public void merchantAccountIsNullWhenNoCurrentBaseStore()
    {
        when(baseStoreService.getCurrentBaseStore()).thenReturn(null);

        assertNull(service.getConfiguredAdyenMerchantAccount());
    }

    @Test
    public void blankMerchantAccountIsTreatedAsUnset()
    {
        when(recurlyConfig.getAdyenGatewayMerchantAccount()).thenReturn("   ");

        assertNull(service.getConfiguredAdyenMerchantAccount());
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

    /**
     * The mirror of {@link #readsFeatureFlagsFromRecurlyConfiguration()}: each flag needs a true case and
     * a false case, and they must not be read from each other.
     */
    @Test
    public void readsFeatureFlagsIndependentlyOfEachOther() throws Exception
    {
        when(recurlyConfig.getExternalNtidFeatureEnabled()).thenReturn(false);
        when(recurlyConfig.getWalletEnabled()).thenReturn(true);

        assertTrue(service.isWalletEnabled());
        assertFalse(service.isExternalNtidFeatureEnabled());
    }
}
