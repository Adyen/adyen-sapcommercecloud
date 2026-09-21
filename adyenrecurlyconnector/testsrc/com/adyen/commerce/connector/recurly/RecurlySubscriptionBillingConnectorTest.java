package com.adyen.commerce.connector.recurly;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.adyen.commerce.connector.exception.ConnectorNotConfiguredException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adyen.commerce.connector.dto.AdyenTokenHandle;
import com.adyen.commerce.connector.exception.CapabilityUnsupportedException;
import com.adyen.commerce.connector.dto.PlatformPaymentMethod;
import com.adyen.commerce.connector.dto.PaymentMethodSource;
import com.adyen.commerce.connector.dto.PaymentMethodChoice;
import com.adyen.commerce.connector.dto.PaymentMethodChangeSupport;
import com.adyen.commerce.connector.dto.PaymentMethodEnrollmentEffect;
import com.adyen.commerce.connector.dto.PaymentMethodChangeScope;
import com.adyen.commerce.connector.dto.PaymentMethodChangeRequest;
import com.adyen.commerce.connector.dto.PaymentMethodChangeOutcome;
import com.adyen.commerce.connector.dto.BillingCustomerRef;
import com.adyen.commerce.connector.dto.BillingPaymentMethodRef;
import com.adyen.commerce.connector.dto.BillingSubscriptionRef;
import com.adyen.commerce.connector.dto.CancelReason;
import com.adyen.commerce.connector.dto.CancellationTiming;
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
import com.adyen.commerce.connector.log.ConnectorLogContext;
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
    public void setUp() throws ConnectorNotConfiguredException {
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

    /**
     * The HTTP transport labels its own lines from this scope rather than guessing the operation from the
     * URL, so the scope must be open while the delegate runs and closed after, since the thread is pooled.
     */
    @Test
    public void anSpiCallPublishesItsOperationForTheLayersUnderneath() throws Exception
    {
        final Map<String, String> observed = new HashMap<>();
        when(apiClient.ensureCustomer(any(), any(), any(), any())).thenAnswer(invocation ->
        {
            observed.put(ConnectorLogContext.PLATFORM,
                    ConnectorLogContext.current(ConnectorLogContext.PLATFORM));
            observed.put(ConnectorLogContext.OPERATION,
                    ConnectorLogContext.current(ConnectorLogContext.OPERATION));
            return "code-customer";
        });

        connector.ensureCustomer(new CustomerSyncRequest("customer", "customer@example.com", "Ada", "Lovelace", Map.of()));

        assertEquals("RECURLY", observed.get(ConnectorLogContext.PLATFORM));
        assertEquals("ensure_customer", observed.get(ConnectorLogContext.OPERATION));
        assertNull(ConnectorLogContext.current(ConnectorLogContext.OPERATION));
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
        when(apiClient.importAdyenToken(any(), any(), any(), any(), any(), any())).thenReturn("billing-1");
        final AdyenTokenHandle token = new AdyenTokenHandle("MERCHANT", "customer", "token", "ntid", null);

        final BillingPaymentMethodRef result = connector.importAdyenToken(new TokenImportRequest(
                new BillingCustomerRef(BillingPlatform.RECURLY, "code-customer"), token,
                RecurringProcessingModel.SUBSCRIPTION));

        assertEquals("billing-1::ntid::ntid", result.externalId());
        // The NTID now reaches the client as its own argument; what the client does with it is gated there.
        verify(apiClient).importAdyenToken("code-customer", "customer", "token", null, "ntid", null);
    }

    @Test
    public void importsAdyenTokenWithoutWallet() throws Exception
    {
        when(configService.isWalletEnabled()).thenReturn(false);
        when(configService.getConfiguredAdyenMerchantAccount()).thenReturn("MERCHANT");
        when(apiClient.importAdyenToken(any(), any(), any(), any(), any(), any())).thenReturn("billing-1");
        final AdyenTokenHandle token = new AdyenTokenHandle("MERCHANT", "customer", "token", "ntid", null);

        final BillingPaymentMethodRef result = connector.importAdyenToken(new TokenImportRequest(
                new BillingCustomerRef(BillingPlatform.RECURLY, "code-customer"), token,
                RecurringProcessingModel.SUBSCRIPTION));

        assertEquals("billing-1::ntid::ntid", result.externalId());
    }

    @Test
    public void rejectsTokenImportWhenRecurlyNtidFeatureIsNotConfirmed() throws ConnectorNotConfiguredException {
        when(configService.isExternalNtidFeatureEnabled()).thenReturn(false);
        final AdyenTokenHandle token = new AdyenTokenHandle("MERCHANT", "shopper", "token", "ntid", null);

        assertThrows(PreconditionFailedException.class, () -> connector.importAdyenToken(new TokenImportRequest(
                new BillingCustomerRef(BillingPlatform.RECURLY, "code-customer"), token,
                RecurringProcessingModel.SUBSCRIPTION)));
    }

    @Test
    public void rejectsTokenWithoutNtid() throws ConnectorNotConfiguredException {
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
                CancelReason.REQUESTED_BY_CUSTOMER, CancellationTiming.AT_PERIOD_END, "cancel-key"));

        // Namespaced per operation: the core reuses one key for a subscription's whole lifecycle, and
        // Recurly answers a repeated key with the first response it recorded.
        verify(apiClient).updateSubscription("uuid-sub", "annual", 2, "update-key/update");
        verify(apiClient).cancelAtNextBillDate("uuid-sub", "cancel-key/cancel");
    }

    /**
     * The two timings are different Recurly endpoints: an end-of-period cancellation arriving as a terminate
     * would end service in the middle of a period the customer has paid for.
     */
    @Test
    public void endOfPeriodCancellationNeverTerminates() throws Exception
    {
        connector.cancelSubscription(new SubscriptionCancelRequest(
                new BillingSubscriptionRef(BillingPlatform.RECURLY, "uuid-sub"),
                CancelReason.REQUESTED_BY_CUSTOMER, CancellationTiming.AT_PERIOD_END, "cancel-key"));

        verify(apiClient).cancelAtNextBillDate("uuid-sub", "cancel-key/cancel");
        verify(apiClient, never()).terminate(any(), any());
    }

    /**
     * The keys are namespaced apart here as well as in the core, because Recurly answers a repeated key with
     * the first response it recorded and a terminate it never saw would be reported as succeeded.
     */
    @Test
    public void immediateCancellationTerminatesUnderItsOwnIdempotencyKey() throws Exception
    {
        connector.cancelSubscription(new SubscriptionCancelRequest(
                new BillingSubscriptionRef(BillingPlatform.RECURLY, "uuid-sub"),
                CancelReason.FRAUD, CancellationTiming.IMMEDIATELY, "cancel-key"));

        verify(apiClient).terminate("uuid-sub", "cancel-key/terminate");
        verify(apiClient, never()).cancelAtNextBillDate(any(), any());
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

    // --- payment-method change (stage 5) ---------------------------------------------------------

    /**
     * Off unless both switches are on. Wallet alone must not enable it, because Wallet also decides how a
     * token is imported at subscription creation and a site may have turned it on for that alone.
     */
    @Test
    public void declaresNoPaymentMethodChangeUntilItsOwnSwitchIsOn() {
        when(configService.isPaymentMethodChangeEnabledOrFalse()).thenReturn(false);

        assertFalse(connector.capabilities().paymentMethodChange().isSupported());
    }

    /** With both on it is subscription-scoped, and accepts only what the platform already holds. */
    @Test
    public void declaresASubscriptionScopedChangeOverWhatTheAccountAlreadyHolds() {
        when(configService.isPaymentMethodChangeEnabledOrFalse()).thenReturn(true);

        final PaymentMethodChangeSupport support = connector.capabilities().paymentMethodChange();

        assertEquals(PaymentMethodChangeScope.SUBSCRIPTION, support.scope());
        assertTrue(support.accepts(PaymentMethodSource.ALREADY_ON_PLATFORM));
        // Importing a freshly picked Adyen card needs the network transaction id of the transaction that
        // authorised it, which a token vaulted earlier cannot supply.
        assertFalse(support.accepts(PaymentMethodSource.ADYEN_VAULTED_TOKEN));
    }

    /** A vaulted card is only offerable where importing an external token is confirmed available. */
    @Test
    public void offersTheAdyenVaultOnlyWhereExternalTokenImportIsConfirmed() {
        when(configService.isPaymentMethodChangeEnabledOrFalse()).thenReturn(true);
        when(configService.isExternalNtidFeatureEnabledOrFalse()).thenReturn(false);
        assertFalse(connector.capabilities().paymentMethodChange()
                .accepts(PaymentMethodSource.ADYEN_VAULTED_TOKEN));

        when(configService.isExternalNtidFeatureEnabledOrFalse()).thenReturn(true);
        final PaymentMethodChangeSupport support = connector.capabilities().paymentMethodChange();
        assertTrue(support.accepts(PaymentMethodSource.ADYEN_VAULTED_TOKEN));
        // The platform's own methods stay on offer; the vault is an addition, not a replacement.
        assertTrue(support.accepts(PaymentMethodSource.ALREADY_ON_PLATFORM));
    }

    /** A vaulted card is imported first and the subscription is then pointed at what the import produced. */
    @Test
    public void importsAVaultedCardAndThenPointsTheSubscriptionAtIt() throws Exception {
        when(configService.isPaymentMethodChangeEnabledOrFalse()).thenReturn(true);
        when(configService.isExternalNtidFeatureEnabled()).thenReturn(true);
        when(configService.isExternalNtidFeatureEnabledOrFalse()).thenReturn(true);
        when(configService.getConfiguredAdyenMerchantAccount()).thenReturn("MERCHANT");
        when(apiClient.importAdyenToken(any(), any(), any(), any(), any(), any())).thenReturn("billing-9");

        final PaymentMethodChangeOutcome outcome = connector.changePaymentMethod(new PaymentMethodChangeRequest(
                new BillingCustomerRef(BillingPlatform.RECURLY, "code-customer"),
                new BillingSubscriptionRef(BillingPlatform.RECURLY, "uuid-sub-1"),
                new PaymentMethodChoice.AdyenVaultedToken(new AdyenTokenHandle("MERCHANT", "customer",
                        "token-1", "NTID-42", null)),
                "key-1"));

        // The reference the import returns packs the NTID; only the billing info id may be assigned.
        verify(apiClient).assignBillingInfo(eq("uuid-sub-1"), eq("billing-9"), any());
        assertEquals(PaymentMethodChangeScope.SUBSCRIPTION, outcome.appliedScope());
    }

    /** No network transaction id, no import: the refusal is the import path's own guard, not a new one. */
    @Test
    public void refusesAVaultedCardAdyenReportsNoAuthorisationFor() throws Exception {
        when(configService.isPaymentMethodChangeEnabledOrFalse()).thenReturn(true);
        when(configService.isExternalNtidFeatureEnabled()).thenReturn(true);
        when(configService.isExternalNtidFeatureEnabledOrFalse()).thenReturn(true);
        when(configService.getConfiguredAdyenMerchantAccount()).thenReturn("MERCHANT");

        final PaymentMethodChangeRequest request = new PaymentMethodChangeRequest(
                new BillingCustomerRef(BillingPlatform.RECURLY, "code-customer"),
                new BillingSubscriptionRef(BillingPlatform.RECURLY, "uuid-sub-1"),
                new PaymentMethodChoice.AdyenVaultedToken(new AdyenTokenHandle("MERCHANT", "customer",
                        "token-1", null, null)),
                "key-1");

        assertThrows(PreconditionFailedException.class, () -> connector.changePaymentMethod(request));
        verify(apiClient, never()).assignBillingInfo(any(), any(), any());
    }

    /**
     * Activation stores the packed reference and a repoint stores the bare id. Both name the same billing
     * info, and the page compares whichever it finds against what listBillingInfos returned.
     */
    @Test
    public void leavesTheAccountDefaultAloneUnlessTheStoreAsksForIt() throws Exception {
        when(configService.isPaymentMethodChangeEnabledOrFalse()).thenReturn(true);

        connector.changePaymentMethod(repointTo("billing-9"));

        verify(apiClient).assignBillingInfo(eq("uuid-sub-1"), eq("billing-9"), any());
        verify(apiClient, never()).promoteBillingInfoToPrimary(any(), any(), any());
    }

    @Test
    public void alsoMakesTheChosenCardTheAccountDefaultWhereTheStoreAsksForIt() throws Exception {
        when(configService.isPaymentMethodChangeEnabledOrFalse()).thenReturn(true);
        when(configService.isPromoteChosenCardToPrimaryEnabled()).thenReturn(true);
        when(apiClient.listBillingInfos("code-customer")).thenReturn(List.of(
                new PlatformPaymentMethod("billing-9", "Visa 4242", null, false)));

        connector.changePaymentMethod(repointTo("billing-9"));

        verify(apiClient).promoteBillingInfoToPrimary(eq("code-customer"), eq("billing-9"), any());
    }

    /**
     * Putting one card behind several subscriptions calls this once per subscription, and promotion is
     * account-wide: Recurly retries collection on unpaid invoices each time, so a card that is already the
     * default must not be promoted again.
     */
    @Test
    public void doesNotPromoteACardThatIsAlreadyTheAccountDefault() throws Exception {
        when(configService.isPaymentMethodChangeEnabledOrFalse()).thenReturn(true);
        when(configService.isPromoteChosenCardToPrimaryEnabled()).thenReturn(true);
        when(apiClient.listBillingInfos("code-customer")).thenReturn(List.of(
                new PlatformPaymentMethod("billing-9", "Visa 4242", null, true)));

        connector.changePaymentMethod(repointTo("billing-9"));

        verify(apiClient).assignBillingInfo(eq("uuid-sub-1"), eq("billing-9"), any());
        verify(apiClient, never()).promoteBillingInfoToPrimary(any(), any(), any());
    }

    /**
     * The subscription is already billing to the card by then, so a failed promotion leaves the shopper's
     * request fulfilled; reporting a failed change would be false.
     */
    @Test
    public void aFailedPromotionDoesNotFailTheChange() throws Exception {
        when(configService.isPaymentMethodChangeEnabledOrFalse()).thenReturn(true);
        when(configService.isPromoteChosenCardToPrimaryEnabled()).thenReturn(true);
        doThrow(new PreconditionFailedException("nope"))
                .when(apiClient).promoteBillingInfoToPrimary(any(), any(), any());

        final PaymentMethodChangeOutcome outcome = connector.changePaymentMethod(repointTo("billing-9"));

        assertEquals(PaymentMethodChangeScope.SUBSCRIPTION, outcome.appliedScope());
    }

    private PaymentMethodChangeRequest repointTo(final String billingInfoId) {
        return new PaymentMethodChangeRequest(
                new BillingCustomerRef(BillingPlatform.RECURLY, "code-customer"),
                new BillingSubscriptionRef(BillingPlatform.RECURLY, "uuid-sub-1"),
                new PaymentMethodChoice.AlreadyOnPlatform(billingInfoId),
                "key-1");
    }

    @Test
    public void narrowsEitherShapeOfStoredReferenceToTheListedBillingInfoId() {
        assertEquals("billing-1", connector.listedPaymentMethodId("billing-1::ntid::NTID-42"));
        assertEquals("billing-1", connector.listedPaymentMethodId("billing-1"));
        // Never throws over a label: an unrecorded or malformed reference simply marks nothing.
        assertEquals("", connector.listedPaymentMethodId(null));
        assertEquals("", connector.listedPaymentMethodId(""));
    }

    @Test
    public void declaresNoHostedPageUntilItsOwnSwitchIsOn() {
        when(configService.isHostedAccountManagementEnabledOrFalse()).thenReturn(false);

        assertFalse(connector.capabilities().paymentMethodEnrollment().isOffered());
    }

    /**
     * Recurly's hosted pages show and edit the primary billing info only, so what the shopper does there
     * replaces the card on file rather than adding one; the page says that before they leave.
     */
    @Test
    public void declaresThatItsHostedPageReplacesTheCardOnFile() {
        when(configService.isHostedAccountManagementEnabledOrFalse()).thenReturn(true);

        assertEquals(PaymentMethodEnrollmentEffect.REPLACES_METHOD_ON_FILE,
                connector.capabilities().paymentMethodEnrollment().effect());
    }

    /** The two switches are independent: neither one turns the other on. */
    @Test
    public void offersTheHostedPageWithoutOfferingARepoint() {
        when(configService.isHostedAccountManagementEnabledOrFalse()).thenReturn(true);
        when(configService.isPaymentMethodChangeEnabledOrFalse()).thenReturn(false);

        final ConnectorCapabilities capabilities = connector.capabilities();

        assertTrue(capabilities.paymentMethodEnrollment().isOffered());
        assertFalse(capabilities.paymentMethodChange().isSupported());
    }

    @Test
    public void mintsTheHostedPageAddressOnlyWhenAsked() throws Exception {
        when(configService.isHostedAccountManagementEnabledOrFalse()).thenReturn(true);
        when(apiClient.hostedAccountManagementUrl("code-customer"))
                .thenReturn("https://mystore.recurly.com/account/abc123");

        assertEquals("https://mystore.recurly.com/account/abc123",
                connector.paymentMethodEnrollmentPage(
                        new BillingCustomerRef(BillingPlatform.RECURLY, "code-customer"))
                        .orElseThrow().url());
    }

    /** Off means no round trip at all, not an address the page then has to suppress. */
    @Test
    public void asksRecurlyForNothingWhileTheHostedPageIsOff() throws Exception {
        when(configService.isHostedAccountManagementEnabledOrFalse()).thenReturn(false);

        assertTrue(connector.paymentMethodEnrollmentPage(
                new BillingCustomerRef(BillingPlatform.RECURLY, "code-customer")).isEmpty());
        verify(apiClient, never()).hostedAccountManagementUrl(any());
    }

    @Test
    public void repointsASubscriptionAtABillingInfoTheAccountAlreadyHolds() throws Exception {
        when(configService.isPaymentMethodChangeEnabledOrFalse()).thenReturn(true);

        final PaymentMethodChangeOutcome outcome = connector.changePaymentMethod(new PaymentMethodChangeRequest(
                new BillingCustomerRef(BillingPlatform.RECURLY, "code-customer"),
                new BillingSubscriptionRef(BillingPlatform.RECURLY, "uuid-sub-1"),
                new PaymentMethodChoice.AlreadyOnPlatform("billing-2"),
                "order-1"));

        verify(apiClient).assignBillingInfo(eq("uuid-sub-1"), eq("billing-2"), any());
        // The scope it reports must equal the scope it declares, or the page describes something else.
        assertEquals(PaymentMethodChangeScope.SUBSCRIPTION, outcome.appliedScope());
        assertEquals("billing-2", outcome.paymentMethod().externalId());
    }

    /**
     * Offering a vaulted Adyen card here would be an import, and Recurly requires a network transaction id
     * for one, which a token vaulted earlier cannot supply.
     */
    @Test
    public void refusesACardFromTheAdyenVaultWhileExternalTokenImportIsUnavailable() {
        when(configService.isPaymentMethodChangeEnabledOrFalse()).thenReturn(true);
        when(configService.isExternalNtidFeatureEnabledOrFalse()).thenReturn(false);

        assertThrows(CapabilityUnsupportedException.class,
                () -> connector.changePaymentMethod(new PaymentMethodChangeRequest(
                        new BillingCustomerRef(BillingPlatform.RECURLY, "code-customer"),
                        new BillingSubscriptionRef(BillingPlatform.RECURLY, "uuid-sub-1"),
                        new PaymentMethodChoice.AdyenVaultedToken(
                                new AdyenTokenHandle("MERCHANT", "shopper-1", "tok-1", "ntid-1", null)),
                        "order-1")));
    }

    /** Even the supported choice is refused while the switch is off, because configuration can change. */
    @Test
    public void refusesTheChangeWhileTheStoreSwitchIsOff() {
        when(configService.isPaymentMethodChangeEnabledOrFalse()).thenReturn(false);

        assertThrows(CapabilityUnsupportedException.class,
                () -> connector.changePaymentMethod(new PaymentMethodChangeRequest(
                        new BillingCustomerRef(BillingPlatform.RECURLY, "code-customer"),
                        new BillingSubscriptionRef(BillingPlatform.RECURLY, "uuid-sub-1"),
                        new PaymentMethodChoice.AlreadyOnPlatform("billing-2"),
                        "order-1")));
    }

    /** Nothing is listed while the feature is off - the core must not be handed options it cannot offer. */
    @Test
    public void listsNothingWhileTheFeatureIsOff() throws Exception {
        when(configService.isPaymentMethodChangeEnabledOrFalse()).thenReturn(false);

        assertTrue(connector.listPaymentMethods(
                new BillingCustomerRef(BillingPlatform.RECURLY, "code-customer")).isEmpty());
        verify(apiClient, never()).listBillingInfos(any());
    }
}
