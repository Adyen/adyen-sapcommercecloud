package com.adyen.commerce.connector.recurly.client.impl;

import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
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
import com.adyen.commerce.connector.exception.BillingException;
import com.adyen.commerce.connector.exception.RetryableBillingException;
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
                        "+48123456789", true));

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
    public void unconfirmedBillingAddressContributesItsAddressButNotItsName() throws Exception
    {
        when(configService.isWalletEnabled()).thenReturn(true);
        when(configService.getGatewayCode()).thenReturn("adyen-gateway");
        when(httpClient.get(BASE + "/accounts/code-customer/billing_infos", auth, ACCEPT))
                .thenReturn(new RecurlyHttpResponse(HTTP_OK, "[]"));
        when(httpClient.post(eq(BASE + "/accounts/code-customer/billing_infos"), eq(auth), eq(ACCEPT), any(), any()))
                .thenReturn(new RecurlyHttpResponse(HTTP_CREATED, "{\"id\":\"billing-1\"}"));

        // Derived from the delivery address, so the name is the recipient's, not the cardholder's.
        client.importAdyenToken("code-customer", "shopper-1", "token-1", null,
                new BillingAddress("Bob", "Recipient", "9 Ship St", null, "Berlin", null, "10115", "DE", null, false));

        final ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(httpClient).post(eq(BASE + "/accounts/code-customer/billing_infos"), eq(auth), eq(ACCEPT),
                body.capture(), any());
        assertFalse(body.getValue().contains("\"first_name\""));
        assertFalse(body.getValue().contains("\"last_name\""));
        assertTrue(body.getValue().contains("\"city\":\"Berlin\""));
    }

    @Test
    public void unconfirmedBillingAddressDoesNotNameANewlyCreatedAccount() throws Exception
    {
        when(configService.isWalletEnabled()).thenReturn(false);
        when(configService.getGatewayCode()).thenReturn("adyen-gateway");
        when(httpClient.get(BASE + "/accounts/code-customer", auth, ACCEPT))
                .thenReturn(new RecurlyHttpResponse(HTTP_NOT_FOUND, ""));
        when(httpClient.post(eq(BASE + "/accounts"), eq(auth), eq(ACCEPT), any(), any()))
                .thenReturn(new RecurlyHttpResponse(HTTP_CREATED, "{\"id\":\"account-1\"}"));
        when(httpClient.get(BASE + "/accounts/code-customer/billing_info", auth, ACCEPT))
                .thenReturn(new RecurlyHttpResponse(HTTP_OK, "{\"id\":\"billing-1\"}"));

        client.importAdyenToken("code-customer", "shopper-1", "token-1", null,
                new BillingAddress("Bob", "Recipient", "9 Ship St", null, "Berlin", null, "10115", "DE", null, false));

        final ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(httpClient).post(eq(BASE + "/accounts"), eq(auth), eq(ACCEPT), body.capture(), any());
        // The account outlives this order; naming it after a gift recipient would misaddress every
        // future invoice and dunning email.
        assertFalse(body.getValue().contains("\"Recipient\""));
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
                new BillingAddress("Ada", "Lovelace", "1 Main St", null, "Warsaw", null, "00-001", "PL", null, true)));

        final ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(httpClient).post(eq(BASE + "/accounts"), eq(auth), eq(ACCEPT), body.capture(),
                eq("code-customer/primary-adyen/" + fingerprint("token-1")));
        assertTrue(body.getValue().contains("\"code\":\"customer\""));
        assertTrue(body.getValue().contains("\"billing_info\":"));
        assertTrue(body.getValue().contains("\"gateway_code\":\"adyen-gateway\""));
        assertTrue(body.getValue().contains("\"first_name\":\"Ada\""));
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

    @Test
    public void pastDueLookupFollowsSiteRelativeNextPage() throws Exception
    {
        // Recurly hands back "next" as a path under the site, not as an absolute URL.
        final String nextPath = "/sites/site-1/accounts/account-1/invoices?cursor=page-2&state=past_due&limit=200";
        when(httpClient.get(BASE + "/subscriptions/uuid-subscription-1", auth, ACCEPT))
                .thenReturn(new RecurlyHttpResponse(HTTP_OK, subscriptionJson("active", true)));
        when(httpClient.get(BASE + "/accounts/account-1/invoices?state=past_due&limit=200", auth, ACCEPT))
                .thenReturn(new RecurlyHttpResponse(HTTP_OK,
                        pastDueInvoicePage(true, nextPath, "another-subscription")));
        when(httpClient.get(BASE + nextPath, auth, ACCEPT))
                .thenReturn(new RecurlyHttpResponse(HTTP_OK, pastDueInvoicePage(false, null, "subscription-1")));

        assertEquals(NormalizedSubscriptionStatus.PAST_DUE,
                client.fetchSubscription("uuid-subscription-1").status());

        verify(httpClient).get(BASE + nextPath, auth, ACCEPT);
    }

    @Test
    public void pastDueLookupRejectsNextPageOnAnotherHost() throws Exception
    {
        when(httpClient.get(BASE + "/subscriptions/uuid-subscription-1", auth, ACCEPT))
                .thenReturn(new RecurlyHttpResponse(HTTP_OK, subscriptionJson("active", true)));
        when(httpClient.get(BASE + "/accounts/account-1/invoices?state=past_due&limit=200", auth, ACCEPT))
                .thenReturn(new RecurlyHttpResponse(HTTP_OK, pastDueInvoicePage(true,
                        "https://evil.example.com/accounts/account-1/invoices?cursor=page-2",
                        "another-subscription")))
                .thenReturn(new RecurlyHttpResponse(HTTP_OK, pastDueInvoicePage(true,
                        "//evil.example.com/accounts/account-1/invoices?cursor=page-2", "another-subscription")));

        final TerminalBillingException absolute = assertThrows(TerminalBillingException.class,
                () -> client.fetchSubscription("uuid-subscription-1"));
        assertEquals("Recurly invoice pagination returned an unexpected URL", absolute.getMessage());

        // A protocol-relative "next" carries its own authority, so joining it to the base must not
        // launder a foreign host into something that passes the guard.
        assertThrows(TerminalBillingException.class, () -> client.fetchSubscription("uuid-subscription-1"));
        verify(httpClient, never()).get(contains("evil.example.com"), any(), any());
    }

    @Test(timeout = 5000)
    public void pastDueLookupStopsWhenPaginationPointsBackAtAPageAlreadyRead() throws Exception
    {
        final String firstPagePath = "/accounts/account-1/invoices?state=past_due&limit=200";
        when(httpClient.get(BASE + "/subscriptions/uuid-subscription-1", auth, ACCEPT))
                .thenReturn(new RecurlyHttpResponse(HTTP_OK, subscriptionJson("active", true)));
        when(httpClient.get(BASE + firstPagePath, auth, ACCEPT))
                .thenReturn(new RecurlyHttpResponse(HTTP_OK,
                        pastDueInvoicePage(true, firstPagePath, "another-subscription")));

        final TerminalBillingException exception = assertThrows(TerminalBillingException.class,
                () -> client.fetchSubscription("uuid-subscription-1"));

        assertEquals("Recurly invoice pagination repeated a page", exception.getMessage());
    }

    /**
     * {@code expired} keeps this about the identifier alone: it is not past-due eligible, so no invoice
     * page has to be stubbed alongside the subscription just to reach the identifier assertion.
     */
    @Test
    public void subscriptionWithoutUuidKeepsItsPlainIdentifier() throws Exception
    {
        when(httpClient.get(BASE + "/subscriptions/short-subscription-id", auth, ACCEPT))
                .thenReturn(new RecurlyHttpResponse(HTTP_OK, "{\"id\":\"short-subscription-id\","
                        + "\"account\":{\"id\":\"account-1\"},\"state\":\"expired\","
                        + "\"plan\":{\"code\":\"monthly\"},\"quantity\":1}"));

        final NormalizedSubscription result = client.fetchSubscription("short-subscription-id");

        assertEquals("short-subscription-id", result.subscription().externalId());
        assertEquals(NormalizedSubscriptionStatus.EXPIRED, result.status());
    }

    /**
     * Recurly's {@code canceled} and Chargebee's {@code non_renewing} are the same real-world state — the
     * subscription stops renewing but keeps serving the customer to the end of the term — so one normalized
     * vocabulary has to describe them the same way. Reporting CANCELLED here would revoke entitlement the
     * customer has already paid for, and would take the reference out of the reconciliation sweep, which
     * excludes terminal statuses, for the whole remainder of the term.
     */
    @Test
    public void cancelledButStillServingTermIsActiveWithAPendingEnd() throws Exception
    {
        when(httpClient.get(BASE + "/subscriptions/uuid-subscription-1", auth, ACCEPT))
                .thenReturn(new RecurlyHttpResponse(HTTP_OK, subscriptionJson("canceled", false)));
        when(httpClient.get(BASE + "/accounts/account-1/invoices?state=past_due&limit=200", auth, ACCEPT))
                .thenReturn(new RecurlyHttpResponse(HTTP_OK,
                        "{\"object\":\"list\",\"has_more\":false,\"data\":[]}"));

        final NormalizedSubscription result = client.fetchSubscription("uuid-subscription-1");

        assertEquals(NormalizedSubscriptionStatus.ACTIVE, result.status());
        assertTrue(result.cancelAtPeriodEnd());
        assertEquals(java.time.Instant.parse("2026-09-01T00:00:00Z"), result.currentPeriodEnd());
    }

    /**
     * Recurly is still collecting the invoice for the term a cancelled subscription is serving out, so that
     * term's dunning must still surface. Being ACTIVE rather than CANCELLED is what keeps it eligible for
     * the past-due lookup at all, and the pending end has to survive the PAST_DUE override.
     */
    @Test
    public void cancelledSubscriptionWithAnUnpaidInvoiceIsStillPastDue() throws Exception
    {
        when(httpClient.get(BASE + "/subscriptions/uuid-subscription-1", auth, ACCEPT))
                .thenReturn(new RecurlyHttpResponse(HTTP_OK, subscriptionJson("canceled", false)));
        when(httpClient.get(BASE + "/accounts/account-1/invoices?state=past_due&limit=200", auth, ACCEPT))
                .thenReturn(new RecurlyHttpResponse(HTTP_OK, "{\"object\":\"list\",\"has_more\":false,"
                        + "\"data\":[{\"subscription_ids\":[\"subscription-1\"]}]}"));

        final NormalizedSubscription result = client.fetchSubscription("uuid-subscription-1");

        assertEquals(NormalizedSubscriptionStatus.PAST_DUE, result.status());
        assertTrue(result.cancelAtPeriodEnd());
    }

    /**
     * The pending end must come from the state and not only from {@code auto_renew}: a payload that omits
     * the flag defaults it to "renewing", which would contradict the {@code canceled} state next to it and
     * tell the storefront the subscription is going to renew.
     */
    @Test
    public void pendingEndIsDerivedFromTheStateWhenAutoRenewIsAbsent() throws Exception
    {
        when(httpClient.get(BASE + "/subscriptions/uuid-subscription-1", auth, ACCEPT))
                .thenReturn(new RecurlyHttpResponse(HTTP_OK, "{\"uuid\":\"subscription-1\","
                        + "\"account\":{\"id\":\"account-1\"},\"state\":\"canceled\","
                        + "\"plan\":{\"code\":\"monthly\"},\"quantity\":1}"));
        when(httpClient.get(BASE + "/accounts/account-1/invoices?state=past_due&limit=200", auth, ACCEPT))
                .thenReturn(new RecurlyHttpResponse(HTTP_OK,
                        "{\"object\":\"list\",\"has_more\":false,\"data\":[]}"));

        assertTrue(client.fetchSubscription("uuid-subscription-1").cancelAtPeriodEnd());
    }

    /**
     * "It ended" is EXPIRED, and it is EXPIRED on both adapters — Chargebee maps its own {@code cancelled},
     * which is the state a stopped subscription reaches there, to the same value. CANCELLED is produced by
     * neither, so a consumer asking whether a subscription has ended gets one answer whichever platform
     * served it. Recurly's {@code expired} is the state a subscription reaches once its term has run out.
     */
    @Test
    public void onlyAnEndedTermMapsToATerminalStatus()
    {
        assertEquals(NormalizedSubscriptionStatus.ACTIVE, client.mapStatus("active"));
        assertEquals(NormalizedSubscriptionStatus.ACTIVE, client.mapStatus("canceled"));
        assertEquals(NormalizedSubscriptionStatus.EXPIRED, client.mapStatus("expired"));
        assertEquals(NormalizedSubscriptionStatus.PENDING, client.mapStatus("future"));
        assertEquals(NormalizedSubscriptionStatus.PAUSED, client.mapStatus("paused"));
        assertEquals(NormalizedSubscriptionStatus.FAILED, client.mapStatus("failed"));
        // A state Recurly may add later is recorded as UNKNOWN rather than guessed at. UNKNOWN is not one
        // of the sweep's terminal statuses, so the reference keeps being re-read and a later mapping fixes
        // it; guessing EXPIRED would take it out of the sweep for good.
        assertEquals(NormalizedSubscriptionStatus.UNKNOWN, client.mapStatus("a_state_we_do_not_know"));
        assertEquals(NormalizedSubscriptionStatus.UNKNOWN, client.mapStatus(null));
    }

    /**
     * The error path had no coverage at all, which is how a throwing call could be added to the middle
     * of the exception factory without anything going red. These four pin the contract the core's retry
     * policy reads: a failed call produces a classified exception carrying the status and the vendor's
     * explanation - never an exception about the response not looking like a subscription.
     */
    @Test
    public void rateLimitBecomesRetryable() throws Exception
    {
        when(httpClient.get(BASE + "/accounts/code-customer", auth, ACCEPT))
                .thenReturn(new RecurlyHttpResponse(429, "{\"error\":{\"type\":\"rate_limited\","
                        + "\"message\":\"You have exceeded the rate limit\"}}"));

        final BillingException thrown = assertThrows(BillingException.class,
                () -> client.ensureCustomer("customer", null, null, null));

        assertTrue(thrown instanceof RetryableBillingException);
        assertTrue(thrown.isRetryable());
        assertTrue(thrown.getMessage().contains("429"));
        assertTrue(thrown.getMessage().contains("rate_limited"));
    }

    @Test
    public void serverErrorBecomesRetryableEvenWithAnEmptyBody() throws Exception
    {
        when(httpClient.get(BASE + "/accounts/code-customer", auth, ACCEPT))
                .thenReturn(new RecurlyHttpResponse(503, ""));

        final BillingException thrown = assertThrows(BillingException.class,
                () -> client.ensureCustomer("customer", null, null, null));

        assertTrue(thrown.isRetryable());
        assertTrue(thrown.getMessage().contains("503"));
    }

    @Test
    public void unprocessableRequestStaysTerminalAndKeepsTheVendorExplanation() throws Exception
    {
        when(httpClient.get(BASE + "/accounts/code-customer", auth, ACCEPT))
                .thenReturn(new RecurlyHttpResponse(422, "{\"error\":{\"type\":\"validation\","
                        + "\"message\":\"email is invalid\"}}"));

        final BillingException thrown = assertThrows(BillingException.class,
                () -> client.ensureCustomer("customer", null, null, null));

        assertTrue(thrown instanceof TerminalBillingException);
        assertFalse(thrown.isRetryable());
        // The code alone would not say which field Recurly refused.
        assertTrue(thrown.getMessage().contains("[validation] email is invalid"));
    }

    @Test
    public void aNonJsonErrorBodyStillProducesTheStatusClassifiedException() throws Exception
    {
        when(httpClient.get(BASE + "/accounts/code-customer", auth, ACCEPT))
                .thenReturn(new RecurlyHttpResponse(502, "<html>gateway timeout</html>"));

        final BillingException thrown = assertThrows(BillingException.class,
                () -> client.ensureCustomer("customer", null, null, null));

        assertTrue(thrown.isRetryable());
        assertTrue(thrown.getMessage().contains("502"));
    }

    private String pastDueInvoicePage(final boolean hasMore, final String next, final String subscriptionId)
    {
        return "{\"object\":\"list\",\"has_more\":" + hasMore + ","
                + "\"next\":" + (next == null ? "null" : "\"" + next + "\"") + ","
                + "\"data\":[{\"subscription_ids\":[\"" + subscriptionId + "\"]}]}";
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
