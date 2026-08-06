package com.adyen.commerce.connector.recurly.client.impl;

import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adyen.commerce.connector.dto.BillingAddress;
import com.adyen.commerce.connector.dto.CardMetadata;
import com.adyen.commerce.connector.dto.NormalizedSubscription;
import com.adyen.commerce.connector.dto.NormalizedSubscriptionStatus;
import com.adyen.commerce.connector.recurly.client.RecurlySubscriptionParams;
import com.adyen.commerce.connector.recurly.config.RecurlyConfigService;
import com.adyen.commerce.connector.recurly.http.RecurlyHttpClient;
import com.adyen.commerce.connector.recurly.http.RecurlyHttpResponse;

import de.hybris.bootstrap.annotations.UnitTest;

@UnitTest
public class DefaultRecurlyApiClientTest
{
    private static final String BASE = "https://v3.recurly.com";
    private static final String ACCEPT = "application/vnd.recurly.v2021-02-25+json";

    @Mock
    private RecurlyHttpClient httpClient;
    @Mock
    private RecurlyConfigService configService;

    private DefaultRecurlyApiClient client;
    private String auth;

    @Before
    public void setUp() throws Exception
    {
        MockitoAnnotations.openMocks(this);
        client = new DefaultRecurlyApiClient(httpClient, configService);
        when(configService.getApiBaseUrl()).thenReturn(BASE);
        when(configService.getApiKey()).thenReturn("recurly-key");
        when(configService.getApiVersion()).thenReturn("v2021-02-25");
        when(configService.isWalletEnabled()).thenReturn(true);
        auth = "Basic " + Base64.getEncoder().encodeToString("recurly-key:".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void ensureCustomerDefersMissingAccountWithoutWallet() throws Exception
    {
        when(configService.isWalletEnabled()).thenReturn(false);
        when(httpClient.get(BASE + "/accounts/code-customer", auth, ACCEPT))
                .thenReturn(new RecurlyHttpResponse(HTTP_NOT_FOUND, ""));

        assertEquals("code-customer", client.ensureCustomer("customer", "customer@example.com", "Ada", "Lovelace"));

        verify(httpClient, never()).post(any(), any(), any(), any(), any());
    }

    @Test
    public void ensureCustomerCreatesMissingAccountWithProfile() throws Exception
    {
        when(httpClient.get(BASE + "/accounts/code-customer", auth, ACCEPT))
                .thenReturn(new RecurlyHttpResponse(HTTP_NOT_FOUND, ""));
        when(httpClient.post(eq(BASE + "/accounts"), eq(auth), eq(ACCEPT), any(), eq("code-customer")))
                .thenReturn(new RecurlyHttpResponse(HTTP_CREATED, "{\"id\":\"account-1\"}"));

        assertEquals("code-customer", client.ensureCustomer("customer", "customer@example.com", "Ada", "Lovelace"));

        final ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(httpClient).post(eq(BASE + "/accounts"), eq(auth), eq(ACCEPT), body.capture(), eq("code-customer"));
        assertTrue(body.getValue().contains("\"code\":\"customer\""));
        assertTrue(body.getValue().contains("\"email\":\"customer@example.com\""));
        assertTrue(body.getValue().contains("\"first_name\":\"Ada\""));
        assertTrue(body.getValue().contains("\"last_name\":\"Lovelace\""));
    }

    @Test
    public void ensureCustomerSynchronizesChangedProfile() throws Exception
    {
        when(httpClient.get(BASE + "/accounts/code-customer", auth, ACCEPT)).thenReturn(new RecurlyHttpResponse(
                HTTP_OK, "{\"email\":\"old@example.com\",\"first_name\":\"Ada\",\"last_name\":\"Lovelace\"}"));
        when(httpClient.put(eq(BASE + "/accounts/code-customer"), eq(auth), eq(ACCEPT), any(),
                any())).thenReturn(new RecurlyHttpResponse(HTTP_OK, "{}"));

        client.ensureCustomer("customer", "new@example.com", "Ada", "Lovelace");

        final ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(httpClient).put(eq(BASE + "/accounts/code-customer"), eq(auth), eq(ACCEPT), body.capture(),
                key.capture());
        assertEquals("{\"email\":\"new@example.com\"}", body.getValue());
        assertEquals("code-customer/profile/" + fingerprint(body.getValue()), key.getValue());
    }

    @Test
    public void importAdyenTokenAddsFirstWalletBillingInfo() throws Exception
    {
        when(configService.getGatewayCode()).thenReturn("adyen-gateway");
        when(httpClient.get(BASE + "/accounts/code-customer/billing_infos", auth, ACCEPT))
                .thenReturn(new RecurlyHttpResponse(HTTP_OK, "[]"));
        when(httpClient.post(eq(BASE + "/accounts/code-customer/billing_infos"), eq(auth), eq(ACCEPT), any(),
                any()))
                .thenReturn(new RecurlyHttpResponse(HTTP_CREATED, "{\"id\":\"billing-1\"}"));

        final String billingInfoId = client.importAdyenToken("code-customer", "shopper-1", "token-1",
                new CardMetadata("visa", "1111", null, "03/2030", "credit"),
                new BillingAddress("Ada", "Lovelace", "1 Main St", "Suite 2", "Warsaw", "MZ", "00-001", "PL",
                        "+48123456789"));

        assertEquals("billing-1", billingInfoId);
        final ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(httpClient).post(eq(BASE + "/accounts/code-customer/billing_infos"), eq(auth), eq(ACCEPT),
                body.capture(), key.capture());
        assertEquals("code-customer/adyen/" + fingerprint("token-1"), key.getValue());
        assertTrue(body.getValue().contains("\"gateway_code\":\"adyen-gateway\""));
        assertTrue(body.getValue().contains("\"account_reference\":\"shopper-1\""));
        assertTrue(body.getValue().contains("\"token\":\"token-1\""));
        assertFalse(body.getValue().contains("\"last_four\""));
        assertTrue(body.getValue().contains("\"month\":\"3\""));
        assertTrue(body.getValue().contains("\"year\":\"2030\""));
        assertTrue(body.getValue().contains("\"first_name\":\"Ada\""));
        assertTrue(body.getValue().contains("\"last_name\":\"Lovelace\""));
        assertTrue(body.getValue().contains("\"street1\":\"1 Main St\""));
        assertTrue(body.getValue().contains("\"city\":\"Warsaw\""));
        assertTrue(body.getValue().contains("\"postal_code\":\"00-001\""));
        assertTrue(body.getValue().contains("\"country\":\"PL\""));
        assertFalse(body.getValue().contains("\"primary_payment_method\""));
    }

    @Test
    public void importAdyenTokenCreatesAccountWithPrimaryBillingInfoWithoutWallet() throws Exception
    {
        when(configService.isWalletEnabled()).thenReturn(false);
        when(configService.getGatewayCode()).thenReturn("adyen-gateway");
        when(httpClient.get(BASE + "/accounts/code-customer", auth, ACCEPT))
                .thenReturn(new RecurlyHttpResponse(HTTP_NOT_FOUND, ""));
        when(httpClient.post(eq(BASE + "/accounts"), eq(auth), eq(ACCEPT), any(), any()))
                .thenReturn(new RecurlyHttpResponse(HTTP_CREATED, "{\"id\":\"account-1\"}"));
        when(httpClient.get(BASE + "/accounts/code-customer/billing_info", auth, ACCEPT))
                .thenReturn(new RecurlyHttpResponse(HTTP_OK, "{\"id\":\"billing-1\"}"));

        assertEquals("billing-1", client.importAdyenToken("code-customer", "shopper-1", "token-1", null,
                new BillingAddress("Ada", "Lovelace", "1 Main St", null, "Warsaw", null, "00-001", "PL", null)));

        final ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(httpClient).post(eq(BASE + "/accounts"), eq(auth), eq(ACCEPT), body.capture(),
                eq("code-customer/primary-adyen/" + fingerprint("token-1")));
        assertTrue(body.getValue().contains("\"code\":\"customer\""));
        assertTrue(body.getValue().contains("\"billing_info\":"));
        assertTrue(body.getValue().contains("\"gateway_code\":\"adyen-gateway\""));
        assertTrue(body.getValue().contains("\"account_reference\":\"shopper-1\""));
        assertTrue(body.getValue().contains("\"token\":\"token-1\""));
        assertFalse(body.getValue().contains("\"primary_payment_method\""));
    }

    @Test
    public void importAdyenTokenSetsMissingPrimaryBillingInfoWithoutWallet() throws Exception
    {
        when(configService.isWalletEnabled()).thenReturn(false);
        when(configService.getGatewayCode()).thenReturn("adyen-gateway");
        when(httpClient.get(BASE + "/accounts/code-customer", auth, ACCEPT))
                .thenReturn(new RecurlyHttpResponse(HTTP_OK, "{\"id\":\"account-1\"}"));
        when(httpClient.get(BASE + "/accounts/code-customer/billing_info", auth, ACCEPT))
                .thenReturn(new RecurlyHttpResponse(HTTP_NOT_FOUND, ""));
        when(httpClient.put(eq(BASE + "/accounts/code-customer/billing_info"), eq(auth), eq(ACCEPT), any(), any()))
                .thenReturn(new RecurlyHttpResponse(HTTP_OK, "{\"id\":\"billing-1\"}"));

        assertEquals("billing-1",
                client.importAdyenToken("code-customer", "shopper-1", "token-1", null, null));

        final ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(httpClient).put(eq(BASE + "/accounts/code-customer/billing_info"), eq(auth), eq(ACCEPT), body.capture(),
                eq("code-customer/primary-adyen/" + fingerprint("token-1")));
        assertTrue(body.getValue().contains("\"gateway_code\":\"adyen-gateway\""));
        assertFalse(body.getValue().contains("\"billing_info\":"));
    }

    @Test
    public void importAdyenTokenReusesOnlyMatchingPrimaryBillingInfo() throws Exception
    {
        when(configService.isWalletEnabled()).thenReturn(false);
        when(httpClient.get(BASE + "/accounts/code-customer", auth, ACCEPT))
                .thenReturn(new RecurlyHttpResponse(HTTP_OK, "{\"id\":\"account-1\"}"));
        when(httpClient.get(BASE + "/accounts/code-customer/billing_info", auth, ACCEPT))
                .thenReturn(new RecurlyHttpResponse(HTTP_OK, "{\"id\":\"billing-1\","
                        + "\"gateway_attributes\":{\"account_reference\":\"shopper-1\"},"
                        + "\"payment_gateway_references\":[{\"token\":\"token-1\"}]}"));

        assertEquals("billing-1",
                client.importAdyenToken("code-customer", "shopper-1", "token-1", null, null));

        verify(httpClient, never()).put(any(), any(), any(), any(), any());
        verify(httpClient, never()).post(any(), any(), any(), any(), any());
    }

    @Test
    public void importAdyenTokenAddsNewBillingInfoWhenExistingTokenDoesNotMatch() throws Exception
    {
        when(configService.getGatewayCode()).thenReturn("adyen-gateway");
        when(httpClient.get(BASE + "/accounts/code-customer/billing_infos", auth, ACCEPT))
                .thenReturn(new RecurlyHttpResponse(HTTP_OK, "[{\"id\":\"billing-old\","
                        + "\"gateway_attributes\":{\"account_reference\":\"shopper-1\"},"
                        + "\"payment_gateway_references\":[{\"token\":\"token-old\"}]}]"));
        when(httpClient.post(eq(BASE + "/accounts/code-customer/billing_infos"), eq(auth), eq(ACCEPT), any(),
                any()))
                .thenReturn(new RecurlyHttpResponse(HTTP_CREATED, "{\"id\":\"billing-new\"}"));

        assertEquals("billing-new",
                client.importAdyenToken("code-customer", "shopper-1", "token-1", null, null));

        final ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(httpClient).post(eq(BASE + "/accounts/code-customer/billing_infos"), eq(auth), eq(ACCEPT),
                body.capture(), any());
        assertTrue(body.getValue().contains("\"primary_payment_method\":false"));
    }

    @Test
    public void createSubscriptionUsesUuidIdentifierForWebhookReconciliation() throws Exception
    {
        when(httpClient.post(eq(BASE + "/subscriptions"), eq(auth), eq(ACCEPT), any(), eq("ORDER-1")))
                .thenReturn(new RecurlyHttpResponse(HTTP_CREATED,
                        "{\"id\":\"sub-short-id\",\"uuid\":\"63ab531e1d5b1d47eaf1ef44eeb853c3\"}"));

        final String id = client.createSubscription(new RecurlySubscriptionParams("code-customer", "billing-1",
                "monthly", 2, "EUR", "2030-01-01T00:00:00Z", "ntid-1", "ORDER-1", Map.of()));

        assertEquals("uuid-63ab531e1d5b1d47eaf1ef44eeb853c3", id);
        final ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(httpClient).post(eq(BASE + "/subscriptions"), eq(auth), eq(ACCEPT), body.capture(), eq("ORDER-1"));
        assertTrue(body.getValue().contains("\"account\":{\"code\":\"customer\"}"));
        assertFalse(body.getValue().contains("\"account\":{\"id\":\"code-customer\"}"));
        assertTrue(body.getValue().contains("\"billing_info_id\":\"billing-1\""));
        assertTrue(body.getValue().contains("\"network_transaction_id\":\"ntid-1\""));
        assertTrue(body.getValue().contains("\"starts_at\":\"2030-01-01T00:00:00Z\""));
    }

    @Test
    public void createSubscriptionUsesAccountPrimaryBillingInfoWithoutWallet() throws Exception
    {
        when(configService.isWalletEnabled()).thenReturn(false);
        when(httpClient.post(eq(BASE + "/subscriptions"), eq(auth), eq(ACCEPT), any(), eq("ORDER-1")))
                .thenReturn(new RecurlyHttpResponse(HTTP_CREATED, "{\"uuid\":\"subscription-1\"}"));

        client.createSubscription(new RecurlySubscriptionParams("code-customer", "billing-1", "monthly", 1,
                "USD", "2030-01-01T00:00:00Z", "ntid-1", "ORDER-1", Map.of()));

        final ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(httpClient).post(eq(BASE + "/subscriptions"), eq(auth), eq(ACCEPT), body.capture(), eq("ORDER-1"));
        assertFalse(body.getValue().contains("\"billing_info_id\""));
        assertTrue(body.getValue().contains("\"network_transaction_id\":\"ntid-1\""));
        assertTrue(body.getValue().contains("\"starts_at\":\"2030-01-01T00:00:00Z\""));
    }

    @Test
    public void cancelAtPeriodEndUsesCancelEndpointAndBillDate() throws Exception
    {
        when(httpClient.put(eq(BASE + "/subscriptions/uuid-123/cancel"), eq(auth), eq(ACCEPT), any(), eq("key")))
                .thenReturn(new RecurlyHttpResponse(HTTP_OK, "{}"));

        client.cancelSubscription("uuid-123", true, "key");

        final ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(httpClient).put(eq(BASE + "/subscriptions/uuid-123/cancel"), eq(auth), eq(ACCEPT), body.capture(),
                eq("key"));
        assertEquals("{\"timeframe\":\"bill_date\"}", body.getValue());
    }

    @Test
    public void immediateCancelTerminatesWithDelete() throws Exception
    {
        when(httpClient.delete(BASE + "/subscriptions/uuid-123", auth, ACCEPT, "key"))
                .thenReturn(new RecurlyHttpResponse(HTTP_OK, "{}"));

        client.cancelSubscription("uuid-123", false, "key");

        verify(httpClient).delete(BASE + "/subscriptions/uuid-123", auth, ACCEPT, "key");
    }

    @Test
    public void resolvesInvoiceSubscriptionIdsFromInvoiceNumber() throws Exception
    {
        when(httpClient.get(BASE + "/invoices/number-1031", auth, ACCEPT)).thenReturn(new RecurlyHttpResponse(
                HTTP_OK, "{\"subscription_ids\":[\"first\",\"uuid-second\"],\"state\":\"paid\"}"));

        assertEquals(java.util.List.of("uuid-first", "uuid-second"),
                client.resolveWebhookSubscriptionIds("charge_invoice", "number-1031"));
    }

    @Test
    public void resolvesPaymentThroughItsInvoice() throws Exception
    {
        when(httpClient.get(BASE + "/transactions/uuid-payment", auth, ACCEPT))
                .thenReturn(new RecurlyHttpResponse(HTTP_OK, "{\"invoice\":{\"id\":\"invoice-1\"}}"));
        when(httpClient.get(BASE + "/invoices/invoice-1", auth, ACCEPT))
                .thenReturn(new RecurlyHttpResponse(HTTP_OK, "{\"subscription_ids\":[\"subscription-1\"]}"));

        assertEquals(java.util.List.of("uuid-subscription-1"),
                client.resolveWebhookSubscriptionIds("payment", "uuid-payment"));
    }

    @Test
    public void fetchSubscriptionReturnsNormalizedLiveState() throws Exception
    {
        when(httpClient.get(BASE + "/subscriptions/uuid-subscription-1", auth, ACCEPT))
                .thenReturn(new RecurlyHttpResponse(HTTP_OK, subscriptionJson("active", true)));
        when(httpClient.get(BASE + "/accounts/account-1/invoices?state=past_due&limit=200", auth, ACCEPT))
                .thenReturn(new RecurlyHttpResponse(HTTP_OK,
                        "{\"object\":\"list\",\"has_more\":false,\"data\":[]}"));

        final NormalizedSubscription result = client.fetchSubscription("uuid-subscription-1");

        assertEquals("uuid-subscription-1", result.subscription().externalId());
        assertEquals(NormalizedSubscriptionStatus.ACTIVE, result.status());
        assertEquals("monthly", result.planId());
        assertEquals(Integer.valueOf(2), result.quantity());
        assertEquals(java.time.Instant.parse("2026-08-01T00:00:00Z"), result.currentPeriodStart());
        assertEquals(java.time.Instant.parse("2026-09-01T00:00:00Z"), result.currentPeriodEnd());
        assertFalse(result.cancelAtPeriodEnd());
    }

    @Test
    public void fetchSubscriptionDerivesPastDueFromMatchingInvoice() throws Exception
    {
        when(httpClient.get(BASE + "/subscriptions/uuid-subscription-1", auth, ACCEPT))
                .thenReturn(new RecurlyHttpResponse(HTTP_OK, subscriptionJson("active", true)));
        when(httpClient.get(BASE + "/accounts/account-1/invoices?state=past_due&limit=200", auth, ACCEPT))
                .thenReturn(new RecurlyHttpResponse(HTTP_OK, "{\"object\":\"list\",\"has_more\":false,"
                        + "\"data\":[{\"subscription_ids\":[\"subscription-1\"]}]}"));

        assertEquals(NormalizedSubscriptionStatus.PAST_DUE,
                client.fetchSubscription("uuid-subscription-1").status());
    }

    @Test
    public void failedSubscriptionIsNotMisclassifiedAsPastDue() throws Exception
    {
        when(httpClient.get(BASE + "/subscriptions/uuid-subscription-1", auth, ACCEPT))
                .thenReturn(new RecurlyHttpResponse(HTTP_OK, subscriptionJson("failed", false)));

        final NormalizedSubscription result = client.fetchSubscription("uuid-subscription-1");

        assertEquals(NormalizedSubscriptionStatus.FAILED, result.status());
        assertTrue(result.cancelAtPeriodEnd());
        verify(httpClient, never()).get(BASE + "/accounts/account-1/invoices?state=past_due&limit=200", auth,
                ACCEPT);
    }

    private String subscriptionJson(final String state, final boolean autoRenew)
    {
        return "{\"id\":\"short-subscription-id\",\"uuid\":\"subscription-1\","
                + "\"account\":{\"id\":\"account-1\"},\"state\":\"" + state + "\","
                + "\"plan\":{\"code\":\"monthly\"},\"quantity\":2,\"auto_renew\":" + autoRenew + ","
                + "\"current_period_started_at\":\"2026-08-01T00:00:00Z\","
                + "\"current_period_ends_at\":\"2026-09-01T00:00:00Z\","
                + "\"updated_at\":\"2026-08-05T12:00:00Z\"}";
    }

    private static UUID fingerprint(final String value)
    {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
