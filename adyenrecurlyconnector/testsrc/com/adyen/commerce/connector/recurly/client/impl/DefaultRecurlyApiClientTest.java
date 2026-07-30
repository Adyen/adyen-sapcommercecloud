package com.adyen.commerce.connector.recurly.client.impl;

import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adyen.commerce.connector.dto.BillingAddress;
import com.adyen.commerce.connector.dto.CardMetadata;
import com.adyen.commerce.connector.exception.TerminalBillingException;
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
        auth = "Basic " + Base64.getEncoder().encodeToString("recurly-key:".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void ensureCustomerDefersMissingAccountUntilTokenImport() throws Exception
    {
        when(httpClient.get(BASE + "/accounts/code-customer", auth, ACCEPT))
                .thenReturn(new RecurlyHttpResponse(HTTP_NOT_FOUND, ""));

        assertEquals("code-customer", client.ensureCustomer("customer", "customer@example.com", "Ada", "Lovelace"));

        verify(httpClient, never()).post(any(), any(), any(), any(), any());
    }

    @Test
    public void importAdyenTokenCreatesAccountWithNestedBillingInfo() throws Exception
    {
        when(configService.getGatewayCode()).thenReturn("adyen-gateway");
        when(httpClient.get(BASE + "/accounts/code-customer", auth, ACCEPT))
                .thenReturn(new RecurlyHttpResponse(HTTP_NOT_FOUND, ""));
        when(httpClient.post(eq(BASE + "/accounts"), eq(auth), eq(ACCEPT), any(),
                eq("code-customer/billing-info")))
                .thenReturn(new RecurlyHttpResponse(HTTP_CREATED, "{\"id\":\"account-1\"}"));
        when(httpClient.get(BASE + "/accounts/code-customer/billing_info", auth, ACCEPT))
                .thenReturn(new RecurlyHttpResponse(HTTP_OK, "{\"id\":\"billing-1\"}"));

        final String billingInfoId = client.importAdyenToken("code-customer", "shopper-1", "token-1",
                new CardMetadata("visa", "1111", null, "03/2030", "credit"),
                new BillingAddress("Ada", "Lovelace", "1 Main St", "Suite 2", "Warsaw", "MZ", "00-001", "PL",
                        "+48123456789"));

        assertEquals("billing-1", billingInfoId);
        final ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(httpClient).post(eq(BASE + "/accounts"), eq(auth), eq(ACCEPT), body.capture(),
                eq("code-customer/billing-info"));
        assertTrue(body.getValue().contains("\"code\":\"customer\""));
        assertTrue(body.getValue().contains("\"billing_info\":"));
        assertTrue(body.getValue().contains("\"gateway_code\":\"adyen-gateway\""));
        assertTrue(body.getValue().contains("\"account_reference\":\"shopper-1\""));
        assertTrue(body.getValue().contains("\"token\":\"token-1\""));
        assertTrue(body.getValue().contains("\"last_four\":\"1111\""));
        assertTrue(body.getValue().contains("\"month\":\"3\""));
        assertTrue(body.getValue().contains("\"year\":\"2030\""));
        assertTrue(body.getValue().contains("\"first_name\":\"Ada\""));
        assertTrue(body.getValue().contains("\"last_name\":\"Lovelace\""));
        assertTrue(body.getValue().contains("\"street1\":\"1 Main St\""));
        assertTrue(body.getValue().contains("\"city\":\"Warsaw\""));
        assertTrue(body.getValue().contains("\"postal_code\":\"00-001\""));
        assertTrue(body.getValue().contains("\"country\":\"PL\""));
    }

    @Test
    public void importAdyenTokenReusesExistingPrimaryBillingInfo() throws Exception
    {
        when(httpClient.get(BASE + "/accounts/code-customer", auth, ACCEPT))
                .thenReturn(new RecurlyHttpResponse(HTTP_OK, "{\"id\":\"account-1\"}"));
        when(httpClient.get(BASE + "/accounts/code-customer/billing_info", auth, ACCEPT))
                .thenReturn(new RecurlyHttpResponse(HTTP_OK, "{\"id\":\"billing-1\"}"));

        assertEquals("billing-1",
                client.importAdyenToken("code-customer", "shopper-1", "token-1", null, null));

        verify(httpClient, never()).put(any(), any(), any(), any(), any());
        verify(httpClient, never()).post(any(), any(), any(), any(), any());
    }

    @Test(expected = TerminalBillingException.class)
    public void importAdyenTokenRejectsExistingAccountWithoutPrimaryBillingInfo() throws Exception
    {
        when(httpClient.get(BASE + "/accounts/code-customer", auth, ACCEPT))
                .thenReturn(new RecurlyHttpResponse(HTTP_OK, "{\"id\":\"account-1\"}"));
        when(httpClient.get(BASE + "/accounts/code-customer/billing_info", auth, ACCEPT))
                .thenReturn(new RecurlyHttpResponse(HTTP_NOT_FOUND, ""));

        client.importAdyenToken("code-customer", "shopper-1", "token-1", null, null);
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
        assertTrue(body.getValue().contains("\"billing_info_id\":\"billing-1\""));
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
}
