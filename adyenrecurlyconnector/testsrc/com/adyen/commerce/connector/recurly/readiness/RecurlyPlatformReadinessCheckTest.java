package com.adyen.commerce.connector.recurly.readiness;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.recurly.model.RecurlyConfigModel;
import com.adyen.v6.strategy.AdyenMerchantAccountStrategy;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.store.BaseStoreModel;

@UnitTest
public class RecurlyPlatformReadinessCheckTest
{
    private static final String MERCHANT_ACCOUNT = "AdyenECOM";
    private static final String MISMATCH = "adyenGatewayMerchantAccount does not match the store's Adyen merchant account";

    @Mock
    private AdyenMerchantAccountStrategy adyenMerchantAccountStrategy;
    @Mock
    private BaseStoreModel store;
    @Mock
    private RecurlyConfigModel config;

    private RecurlyPlatformReadinessCheck check;

    @Before
    public void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(store.getRecurlyConfig()).thenReturn(config);
        when(config.getSubscriptionApiKey()).thenReturn("api-key");
        when(config.getSubscriptionSiteId()).thenReturn("https://v3.recurly.com");
        when(config.getSubscriptionGatewayAccountId()).thenReturn("gateway-code");
        when(config.getAdyenGatewayMerchantAccount()).thenReturn(MERCHANT_ACCOUNT);
        when(config.getRecurlyWebhookSigningKey()).thenReturn("signing-key");
        when(adyenMerchantAccountStrategy.getWebMerchantAccount(store)).thenReturn(MERCHANT_ACCOUNT);
        check = new RecurlyPlatformReadinessCheck();
        check.setAdyenMerchantAccountStrategy(adyenMerchantAccountStrategy);
    }

    @Test
    public void checksRecurly()
    {
        assertEquals(BillingPlatform.RECURLY, check.platform());
    }

    @Test
    public void completeConfigurationBoundToTheStoreIsReady()
    {
        assertTrue(check.missingSettings(store).isEmpty());
    }

    @Test
    public void missingConfigurationIsReportedOnce()
    {
        when(store.getRecurlyConfig()).thenReturn(null);

        assertEquals(List.of("Recurly configuration"), check.missingSettings(store));
        verify(adyenMerchantAccountStrategy, never()).getWebMerchantAccount(any(BaseStoreModel.class));
    }

    @Test
    public void blankRequiredSettingsAreReportedByAttributeName()
    {
        when(config.getSubscriptionApiKey()).thenReturn("   ");
        when(config.getSubscriptionSiteId()).thenReturn(null);
        when(config.getSubscriptionGatewayAccountId()).thenReturn("");
        when(config.getRecurlyWebhookSigningKey()).thenReturn(null);

        assertEquals(List.of("subscriptionApiKey", "subscriptionSiteId", "subscriptionGatewayAccountId",
                "recurlyWebhookSigningKey"), check.missingSettings(store));
    }

    /**
     * Reported as missing only; comparing a blank with the store's account would add a second, misleading entry.
     */
    @Test
    public void blankGatewayMerchantAccountIsReportedAsMissing()
    {
        when(config.getAdyenGatewayMerchantAccount()).thenReturn(" ");

        assertEquals(List.of("adyenGatewayMerchantAccount"), check.missingSettings(store));
        verify(adyenMerchantAccountStrategy, never()).getWebMerchantAccount(any(BaseStoreModel.class));
    }

    @Test
    public void gatewayBoundToAnotherMerchantAccountIsReported()
    {
        when(adyenMerchantAccountStrategy.getWebMerchantAccount(store)).thenReturn("OtherECOM");

        assertEquals(List.of(MISMATCH), check.missingSettings(store));
    }

    @Test
    public void storeWithoutMerchantAccountIsReportedAsMismatch()
    {
        when(adyenMerchantAccountStrategy.getWebMerchantAccount(store)).thenReturn(null);

        assertEquals(List.of(MISMATCH), check.missingSettings(store));
    }

    @Test
    public void gatewayMerchantAccountIsTrimmedBeforeComparison()
    {
        when(config.getAdyenGatewayMerchantAccount()).thenReturn("  " + MERCHANT_ACCOUNT + " ");

        assertTrue(check.missingSettings(store).isEmpty());
    }
}
