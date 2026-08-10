package com.adyen.commerce.connector.recurly;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adyen.commerce.connector.dto.AdyenTokenHandle;
import com.adyen.commerce.connector.dto.BillingCustomerRef;
import com.adyen.commerce.connector.dto.BillingPaymentMethodRef;
import com.adyen.commerce.connector.dto.BillingSubscriptionRef;
import com.adyen.commerce.connector.dto.CancelReason;
import com.adyen.commerce.connector.dto.ConnectorCapabilities;
import com.adyen.commerce.connector.dto.CustomerSyncRequest;
import com.adyen.commerce.connector.dto.NormalizedBillingEvent;
import com.adyen.commerce.connector.dto.PlanRef;
import com.adyen.commerce.connector.dto.PlanResolutionRequest;
import com.adyen.commerce.connector.dto.RawWebhook;
import com.adyen.commerce.connector.dto.RecurringProcessingModel;
import com.adyen.commerce.connector.dto.SubscriptionCancelRequest;
import com.adyen.commerce.connector.dto.SubscriptionCreateRequest;
import com.adyen.commerce.connector.dto.SubscriptionUpdateRequest;
import com.adyen.commerce.connector.dto.TokenImportRequest;
import com.adyen.commerce.connector.dto.TokenImportStyle;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.PreconditionFailedException;
import com.adyen.commerce.connector.recurly.client.RecurlyApiClient;
import com.adyen.commerce.connector.recurly.client.RecurlySubscriptionParams;
import com.adyen.commerce.connector.recurly.config.RecurlyConfigService;
import com.adyen.commerce.connector.recurly.plan.RecurlyPlanResolver;
import com.adyen.commerce.connector.recurly.webhook.RecurlyWebhookParser;

import de.hybris.bootstrap.annotations.UnitTest;

@UnitTest
public class RecurlySubscriptionBillingConnectorTest
{
    private static final Instant NOW = Instant.parse("2026-07-21T10:00:00Z");

    @Mock
    private RecurlyApiClient apiClient;
    @Mock
    private RecurlyConfigService configService;
    @Mock
    private RecurlyPlanResolver planResolver;
    @Mock
    private RecurlyWebhookParser webhookParser;

    private RecurlySubscriptionBillingConnector connector;

    @Before
    public void setUp()
    {
        MockitoAnnotations.openMocks(this);
        connector = new RecurlySubscriptionBillingConnector(apiClient, configService, planResolver, webhookParser,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(configService.getMinimumStartDelaySeconds()).thenReturn(300);
        when(configService.isExternalNtidFeatureEnabled()).thenReturn(true);
        when(configService.isWalletEnabled()).thenReturn(true);
    }

    @Test
    public void nullStartDateBecomesARecurlySafeFutureDate() throws Exception
    {
        when(apiClient.createSubscription(any())).thenReturn("uuid-subscription");

        connector.createSubscription(request(null));

        final ArgumentCaptor<RecurlySubscriptionParams> params =
                ArgumentCaptor.forClass(RecurlySubscriptionParams.class);
        verify(apiClient).createSubscription(params.capture());
        assertEquals("2026-07-21T10:05:00Z", params.getValue().startsAt());
        assertEquals("billing-1", params.getValue().billingInfoId());
        assertEquals("ntid-1", params.getValue().networkTransactionId());
    }

    @Test
    public void explicitNonFutureStartDateIsRejected()
    {
        assertThrows(PreconditionFailedException.class, () -> connector.createSubscription(request(NOW)));
    }

    @Test
    public void capabilitiesReflectRecurlyRequirements()
    {
        final ConnectorCapabilities capabilities = connector.capabilities();
        assertTrue(capabilities.requiresNetworkTransactionId());
        assertFalse(capabilities.supportsImmediateStart());
        assertFalse(capabilities.supportsPause());
        assertTrue(capabilities.requiresPreConfiguredPlan());
        assertFalse(capabilities.liveTokenValidationOnImport());
        assertEquals(TokenImportStyle.SEPARATE_FIELDS, capabilities.tokenImportStyle());
    }

    @Test
    public void ensureCustomerReturnsRecurlyReference() throws Exception
    {
        when(apiClient.ensureCustomer("customer", "customer@example.com", "Ada", "Lovelace"))
                .thenReturn("code-customer");

        final BillingCustomerRef result = connector.ensureCustomer(
                new CustomerSyncRequest("customer", "customer@example.com", "Ada", "Lovelace", Map.of()));

        assertEquals(BillingPlatform.RECURLY, result.platform());
        assertEquals("code-customer", result.externalId());
    }

    @Test
    public void importsAdyenTokenAndCarriesNtidToSubscriptionReference() throws Exception
    {
        when(configService.getConfiguredAdyenMerchantAccount()).thenReturn("MERCHANT");
        when(apiClient.importAdyenToken(any(), any(), any(), any(), any())).thenReturn("billing-1");
        final AdyenTokenHandle token = new AdyenTokenHandle("MERCHANT", "customer", "token", "ntid", null);

        final BillingPaymentMethodRef result = connector.importAdyenToken(new TokenImportRequest(
                new BillingCustomerRef(BillingPlatform.RECURLY, "code-customer"), token,
                RecurringProcessingModel.SUBSCRIPTION));

        assertEquals("billing-1::ntid::ntid", result.externalId());
        verify(apiClient).importAdyenToken("code-customer", "customer", "token", null, null);
    }

    @Test
    public void importsAdyenTokenWithoutWallet() throws Exception
    {
        when(configService.isWalletEnabled()).thenReturn(false);
        when(configService.getConfiguredAdyenMerchantAccount()).thenReturn("MERCHANT");
        when(apiClient.importAdyenToken(any(), any(), any(), any(), any())).thenReturn("billing-1");
        final AdyenTokenHandle token = new AdyenTokenHandle("MERCHANT", "customer", "token", "ntid", null);

        final BillingPaymentMethodRef result = connector.importAdyenToken(new TokenImportRequest(
                new BillingCustomerRef(BillingPlatform.RECURLY, "code-customer"), token,
                RecurringProcessingModel.SUBSCRIPTION));

        assertEquals("billing-1::ntid::ntid", result.externalId());
    }

    @Test
    public void rejectsTokenImportWhenRecurlyNtidFeatureIsNotConfirmed()
    {
        when(configService.isExternalNtidFeatureEnabled()).thenReturn(false);
        final AdyenTokenHandle token = new AdyenTokenHandle("MERCHANT", "shopper", "token", "ntid", null);

        assertThrows(PreconditionFailedException.class, () -> connector.importAdyenToken(new TokenImportRequest(
                new BillingCustomerRef(BillingPlatform.RECURLY, "code-customer"), token,
                RecurringProcessingModel.SUBSCRIPTION)));
    }

    @Test
    public void rejectsTokenWithoutNtid()
    {
        when(configService.getConfiguredAdyenMerchantAccount()).thenReturn("MERCHANT");
        final AdyenTokenHandle token = new AdyenTokenHandle("MERCHANT", "shopper", "token", null, null);

        assertThrows(PreconditionFailedException.class, () -> connector.importAdyenToken(new TokenImportRequest(
                new BillingCustomerRef(BillingPlatform.RECURLY, "code-customer"), token,
                RecurringProcessingModel.SUBSCRIPTION)));
    }

    @Test
    public void resolvePlanDelegatesToResolver() throws Exception
    {
        final PlanRef plan = new PlanRef("monthly", null);
        when(planResolver.resolve(any())).thenReturn(plan);

        assertSame(plan, connector.resolvePlan(new PlanResolutionRequest("product", Map.of())));
    }

    @Test
    public void updateAndCancelForwardIdempotencyKeys() throws Exception
    {
        connector.updateSubscription(new SubscriptionUpdateRequest(
                new BillingSubscriptionRef(BillingPlatform.RECURLY, "uuid-sub"), new PlanRef("annual", null), 2,
                null, Map.of(), "update-key"));
        connector.cancelSubscription(new SubscriptionCancelRequest(
                new BillingSubscriptionRef(BillingPlatform.RECURLY, "uuid-sub"),
                CancelReason.REQUESTED_BY_CUSTOMER, true, "cancel-key"));

        // Namespaced per operation: the core reuses one key for a subscription's whole lifecycle, and
        // Recurly answers a repeated key with the first response it recorded — so a cancel sharing the
        // create's key could be acknowledged with the stored 201 while billing continued.
        verify(apiClient).updateSubscription("uuid-sub", "annual", 2, "update-key/update");
        verify(apiClient).cancelSubscription("uuid-sub", true, "cancel-key/cancel");
    }

    @Test
    public void parseWebhookDelegatesToParser() throws Exception
    {
        final RawWebhook raw = new RawWebhook(Map.of(), "{}", "signature");
        final NormalizedBillingEvent event = new NormalizedBillingEvent(BillingPlatform.RECURLY,
                com.adyen.commerce.connector.dto.BillingEventType.SUBSCRIPTION_ACTIVATED, "ev-1", "uuid-sub", null, NOW,
                Map.of());
        when(webhookParser.parse(raw)).thenReturn(event);

        assertSame(event, connector.parseWebhook(raw));
    }

    @Test
    public void resolveSubscriptionIdsLooksUpTheInvoiceOrTransactionBehindTheEvent() throws Exception
    {
        final NormalizedBillingEvent event = new NormalizedBillingEvent(BillingPlatform.RECURLY,
                com.adyen.commerce.connector.dto.BillingEventType.INVOICE_PAID, "ev-1", null, "code-customer", NOW,
                java.util.Map.of("resourceType", "charge_invoice", "resourceId", "number-1031"));
        // An invoice can cover several subscriptions, and the event applies to every one of them.
        when(apiClient.resolveWebhookSubscriptionIds("charge_invoice", "number-1031"))
                .thenReturn(java.util.List.of("uuid-a", "uuid-b"));

        assertEquals(java.util.List.of("uuid-a", "uuid-b"), connector.resolveSubscriptionIds(event));
    }

    @Test
    public void resolveSubscriptionIdsPassesThroughWhenTheEventCarriesNoResource() throws Exception
    {
        final NormalizedBillingEvent event = new NormalizedBillingEvent(BillingPlatform.RECURLY,
                com.adyen.commerce.connector.dto.BillingEventType.INVOICE_PAID, "ev-1", null, "code-customer", NOW,
                java.util.Map.of());
        when(apiClient.resolveWebhookSubscriptionIds(null, null)).thenReturn(java.util.List.of());

        assertEquals(java.util.List.of(), connector.resolveSubscriptionIds(event));
    }

    private SubscriptionCreateRequest request(final Instant startsAt)
    {
        return new SubscriptionCreateRequest(new BillingCustomerRef(BillingPlatform.RECURLY, "code-customer"),
                new BillingPaymentMethodRef(BillingPlatform.RECURLY, "billing-1::ntid::ntid-1"),
                new PlanRef("monthly", null), 1, null, "EUR", null, startsAt, Map.of(), "ORDER-1");
    }
}
