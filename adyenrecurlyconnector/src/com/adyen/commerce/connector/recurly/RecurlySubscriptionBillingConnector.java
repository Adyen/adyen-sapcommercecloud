package com.adyen.commerce.connector.recurly;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adyen.commerce.connector.dto.AdyenTokenHandle;
import com.adyen.commerce.connector.dto.BillingCustomerRef;
import com.adyen.commerce.connector.dto.BillingPaymentMethodRef;
import com.adyen.commerce.connector.dto.BillingSubscriptionRef;
import com.adyen.commerce.connector.dto.ConnectorCapabilities;
import com.adyen.commerce.connector.dto.CustomerSyncRequest;
import com.adyen.commerce.connector.dto.NormalizedBillingEvent;
import com.adyen.commerce.connector.dto.NormalizedSubscription;
import com.adyen.commerce.connector.dto.PaymentMethodChangeOutcome;
import com.adyen.commerce.connector.dto.PaymentMethodChangeRequest;
import com.adyen.commerce.connector.dto.PaymentMethodChangeSupport;
import com.adyen.commerce.connector.dto.PaymentMethodEnrollmentSupport;
import com.adyen.commerce.connector.dto.PaymentMethodEnrollmentPage;
import com.adyen.commerce.connector.dto.PaymentMethodEnrollmentEffect;
import com.adyen.commerce.connector.dto.PaymentMethodChoice;
import com.adyen.commerce.connector.dto.PaymentMethodSource;
import com.adyen.commerce.connector.dto.PlatformPaymentMethod;
import com.adyen.commerce.connector.exception.CapabilityUnsupportedException;
import com.adyen.commerce.connector.dto.PaymentMethodChangeScope;
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
import com.adyen.commerce.connector.exception.BillingException;
import com.adyen.commerce.connector.exception.ConnectorNotConfiguredException;
import com.adyen.commerce.connector.exception.PreconditionFailedException;
import com.adyen.commerce.connector.log.ConnectorLogContext;
import com.adyen.commerce.connector.log.ConnectorLogEvent;
import com.adyen.commerce.connector.recurly.client.RecurlyApiClient;
import com.adyen.commerce.connector.recurly.client.RecurlySubscriptionParams;
import com.adyen.commerce.connector.recurly.config.RecurlyConfigService;
import com.adyen.commerce.connector.recurly.plan.RecurlyPlanResolver;
import com.adyen.commerce.connector.recurly.webhook.RecurlyWebhookParser;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;

/**
 * Recurly adapter of the {@link SubscriptionBillingConnector} SPI. This adapter follows the same
 * extension-local architecture as the Chargebee connector: config service, HTTP transport, API client,
 * plan resolver, and one SPI bean.
 *
 * <p>Every SPI entry point opens a {@link ConnectorLogContext} naming the operation, so the lines
 * emitted underneath it - by the API client and by the HTTP transport - carry the same
 * {@code platform}/{@code operation}/{@code correlation_id} without those layers having to work out
 * what they are being used for.</p>
 */
public class RecurlySubscriptionBillingConnector implements SubscriptionBillingConnector {
    private static final Logger LOG = LoggerFactory.getLogger(RecurlySubscriptionBillingConnector.class);

    private static final String EVENT_CONNECTOR_OPERATION = "connector_operation";
    private static final String EVENT_TOKEN_IMPORT_VALIDATION_FAILURE = "token_import_validation_failure";
    private static final String EVENT_WEBHOOK_RESOLUTION = "webhook_resolution";
    private static final String EVENT_RECONCILIATION_GAP = "reconciliation_gap";

    /**
     * Everything except the payment-method change, which depends on configuration and is therefore built
     * per call rather than held here.
     */
    private static final ConnectorCapabilities BASE_CAPABILITIES = new ConnectorCapabilities(
            true,
            false,
            false,
            true,
            false,
            TokenImportStyle.SEPARATE_FIELDS,
            PaymentMethodChangeSupport.NONE,
            PaymentMethodEnrollmentSupport.NONE);

    private final RecurlyApiClient apiClient;
    private final RecurlyConfigService configService;
    private final RecurlyPlanResolver planResolver;
    private final RecurlyWebhookParser webhookParser;
    private final Clock clock;

    public RecurlySubscriptionBillingConnector(final RecurlyApiClient apiClient,
                                               final RecurlyConfigService configService,
                                               final RecurlyPlanResolver planResolver,
                                               final RecurlyWebhookParser webhookParser) {
        this(apiClient, configService, planResolver, webhookParser, Clock.systemUTC());
    }

    RecurlySubscriptionBillingConnector(final RecurlyApiClient apiClient,
                                        final RecurlyConfigService configService,
                                        final RecurlyPlanResolver planResolver,
                                        final RecurlyWebhookParser webhookParser,
                                        final Clock clock) {
        this.apiClient = apiClient;
        this.configService = configService;
        this.planResolver = planResolver;
        this.webhookParser = webhookParser;
        this.clock = clock;
    }

    @Override
    public BillingPlatform platform() {
        return BillingPlatform.RECURLY;
    }

    @Override
    public ConnectorCapabilities capabilities() {
        // Switches in the base store decide all of this, and no accessor throws: capabilities are read on
        // the order-activation path, where an unconfigured store must offer nothing rather than fail a
        // checkout. Repointing and the hosted page are independent - a site may have either alone.
        //
        // SUBSCRIPTION scope because Recurly pins a billing info to one subscription. A card from the Adyen
        // vault is accepted only where external-token import is available, since importing one means
        // sending Recurly the network transaction id of the authorisation that vaulted it.
        final PaymentMethodChangeSupport change = configService.isPaymentMethodChangeEnabledOrFalse()
                ? new PaymentMethodChangeSupport(PaymentMethodChangeScope.SUBSCRIPTION,
                        configService.isExternalNtidFeatureEnabledOrFalse()
                                ? Set.of(PaymentMethodSource.ALREADY_ON_PLATFORM,
                                        PaymentMethodSource.ADYEN_VAULTED_TOKEN)
                                : Set.of(PaymentMethodSource.ALREADY_ON_PLATFORM))
                : PaymentMethodChangeSupport.NONE;

        // REPLACES_METHOD_ON_FILE, not ADDS_METHOD: Recurly's hosted pages show and edit the primary
        // billing info only, so a shopper cannot use them to put a second card in the wallet.
        final PaymentMethodEnrollmentSupport enrollment = configService.isHostedAccountManagementEnabledOrFalse()
                ? new PaymentMethodEnrollmentSupport(PaymentMethodEnrollmentEffect.REPLACES_METHOD_ON_FILE)
                : PaymentMethodEnrollmentSupport.NONE;

        if (!change.isSupported() && !enrollment.isOffered()) {
            return BASE_CAPABILITIES;
        }
        return new ConnectorCapabilities(
                BASE_CAPABILITIES.requiresNetworkTransactionId(),
                BASE_CAPABILITIES.supportsImmediateStart(),
                BASE_CAPABILITIES.supportsPause(),
                BASE_CAPABILITIES.requiresPreConfiguredPlan(),
                BASE_CAPABILITIES.liveTokenValidationOnImport(),
                BASE_CAPABILITIES.tokenImportStyle(),
                change,
                enrollment);
    }

    @Override
    public Optional<PaymentMethodEnrollmentPage> paymentMethodEnrollmentPage(final BillingCustomerRef customer)
            throws BillingException {
        if (!configService.isHostedAccountManagementEnabledOrFalse()) {
            return Optional.empty();
        }
        verifyRecurlyCustomer(customer);
        return Optional.ofNullable(apiClient.hostedAccountManagementUrl(customer.externalId()))
                .map(PaymentMethodEnrollmentPage::new);
    }

    @Override
    public List<PlatformPaymentMethod> listPaymentMethods(final BillingCustomerRef customer)
            throws BillingException {
        if (!configService.isPaymentMethodChangeEnabledOrFalse()) {
            return List.of();
        }
        verifyRecurlyCustomer(customer);
        return apiClient.listBillingInfos(customer.externalId());
    }

    @Override
    public PaymentMethodChangeOutcome changePaymentMethod(final PaymentMethodChangeRequest request)
            throws BillingException {
        final long startedAt = System.nanoTime();
        try (ConnectorLogContext scope = ConnectorLogContext.open(platform(), "change_payment_method")) {
            // Re-checked here and not only by the core: the capability is configuration, and configuration
            // can change between the page being rendered and the form being posted.
            if (!configService.isPaymentMethodChangeEnabledOrFalse()) {
                throw new CapabilityUnsupportedException("Recurly payment-method changes are not enabled for "
                        + "this store; they need Subscriber Wallet and the store's own switch for it");
            }

            // A switch expression, so a third kind of choice is a build failure rather than a fall-through.
            final String billingInfoId = switch (request.choice()) {
                case PaymentMethodChoice.AlreadyOnPlatform onPlatform -> onPlatform.platformPaymentMethodId();
                // Imported first, then assigned. importAdyenToken applies every guard this path needs -
                // external-NTID support, merchant-account binding, token ownership, and the refusal of a
                // handle carrying no network transaction id - and is idempotent on an already-imported
                // token, so a repeated submission reuses the billing info rather than duplicating it.
                case PaymentMethodChoice.AdyenVaultedToken vaulted -> {
                    // Re-checked for the same reason as the change switch above, and reported as a
                    // capability rather than a failure: with external-token import unavailable this is
                    // something Recurly cannot do here, not something that went wrong.
                    if (!configService.isExternalNtidFeatureEnabledOrFalse()) {
                        throw new CapabilityUnsupportedException("Recurly cannot be pointed at a card from "
                                + "the Adyen vault unless external-token import is available: the import "
                                + "carries the network transaction id of the authorisation that vaulted it");
                    }
                    yield RecurlyPaymentMethodReference
                            .parse(importAdyenToken(new TokenImportRequest(request.customer(), vaulted.token(),
                                    RecurringProcessingModel.SUBSCRIPTION)).externalId())
                            .billingInfoId();
                }
            };

            final String subscriptionId = request.subscription().externalId();
            try {
                apiClient.assignBillingInfo(subscriptionId, billingInfoId,
                        operationKey(request.idempotencyKey(), "payment-method"));
            } catch (final BillingException e) {
                ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
                        .failure(startedAt, e)
                        .field("subscription_id", subscriptionId)
                        .field("billing_info_id", billingInfoId)
                        .warn(LOG);
                throw e;
            }
            ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
                    .success(startedAt)
                    .field("subscription_id", subscriptionId)
                    .field("billing_info_id", billingInfoId)
                    .field("applied_scope", PaymentMethodChangeScope.SUBSCRIPTION.name())
                    .info(LOG);
            return new PaymentMethodChangeOutcome(
                    new BillingPaymentMethodRef(BillingPlatform.RECURLY, billingInfoId),
                    PaymentMethodChangeScope.SUBSCRIPTION);
        }
    }

    @Override
    public String configuredAdyenMerchantAccount() {
        return configService.getConfiguredAdyenMerchantAccount();
    }

    @Override
    public BillingCustomerRef ensureCustomer(final CustomerSyncRequest request) throws BillingException {
        final long startedAt = System.nanoTime();
        try (ConnectorLogContext scope = ConnectorLogContext.open(platform(), "ensure_customer")) {
            final String accountId;
            try {
                accountId = apiClient.ensureCustomer(request.customerId(), request.email(), request.firstName(),
                        request.lastName());
            } catch (final BillingException e) {
                // The customer id from the request, not the account id: the call that would have
                // produced an account id is the one that just failed.
                ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
                        .failure(startedAt, e)
                        .field("customer_id", request.customerId())
                        .warn(LOG);
                throw e;
            }
            ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
                    .success(startedAt)
                    .field("customer_id", request.customerId())
                    .field("account_id", accountId)
                    .info(LOG);
            return new BillingCustomerRef(BillingPlatform.RECURLY, accountId);
        }
    }

    @Override
    public BillingPaymentMethodRef importAdyenToken(final TokenImportRequest request) throws BillingException {
        final long startedAt = System.nanoTime();
        try (ConnectorLogContext scope = ConnectorLogContext.open(platform(), "import_token")) {
            final AdyenTokenHandle token = request.token();
            verifyRecurlyCustomer(request.customer());
            verifySubscriptionModel(request.model());
            verifyTokenOwnership(request.customer(), token);
            verifyExternalNtidSupport();
            verifyMerchantAccount(token);
            verifyNetworkTransactionId(token);

            final String billingInfoId;
            try {
                billingInfoId = apiClient.importAdyenToken(request.customer().externalId(), token.shopperReference(),
                        token.storedPaymentMethodId(), token.cardMetadata(), token.networkTransactionId(),
                        request.billingAddress());
            } catch (final BillingException e) {
                ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
                        .failure(startedAt, e)
                        .field("external_id", request.customer().externalId())
                        .field("token_reference", token.storedPaymentMethodId())
                        .field("merchant_account", token.merchantAccount())
                        .field("network_transaction_id_present", Boolean.valueOf(token.hasNetworkTransactionId()))
                        .warn(LOG);
                throw e;
            }
            // Only whether an NTID is present, never its value: it is a scheme-level payment identifier.
            ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
                    .success(startedAt)
                    .field("external_id", request.customer().externalId())
                    .field("token_reference", token.storedPaymentMethodId())
                    .field("billing_info_id", billingInfoId)
                    .field("merchant_account", token.merchantAccount())
                    .field("network_transaction_id_present", Boolean.valueOf(token.hasNetworkTransactionId()))
                    .info(LOG);
            return new BillingPaymentMethodRef(BillingPlatform.RECURLY,
                    RecurlyPaymentMethodReference.encode(billingInfoId, token.networkTransactionId()));
        }
    }

    @Override
    public PlanRef resolvePlan(final PlanResolutionRequest request) throws BillingException {
        final long startedAt = System.nanoTime();
        try (ConnectorLogContext scope = ConnectorLogContext.open(platform(), "resolve_plan")) {
            final PlanRef plan;
            try {
                plan = planResolver.resolve(request);
            } catch (final BillingException e) {
                ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
                        .failure(startedAt, e)
                        .field("product_code", request.productCode())
                        .warn(LOG);
                throw e;
            }
            ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
                    .success(startedAt)
                    .field("product_code", request.productCode())
                    .field("plan_id", planCodeOrNull(plan))
                    .info(LOG);
            return plan;
        }
    }

    @Override
    public BillingSubscriptionRef createSubscription(final SubscriptionCreateRequest request) throws BillingException {
        final long startedAt = System.nanoTime();
        try (ConnectorLogContext scope = ConnectorLogContext.open(platform(), "create_subscription")) {
            try {
                return createSubscriptionInternal(request, startedAt);
            } catch (final BillingException e) {
                // Read through null-tolerant accessors: a failure before the request was fully built is
                // exactly when these are unset, and an NPE raised here would replace the real cause.
                ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
                        .failure(startedAt, e)
                        .field("plan_id", planCodeOrNull(request.plan()))
                        .field("payment_method_reference", externalIdOrNull(request.paymentMethod()))
                        .warn(LOG);
                throw e;
            }
        }
    }

    private BillingSubscriptionRef createSubscriptionInternal(final SubscriptionCreateRequest request,
                                                              final long startedAt) throws BillingException {
        final Instant now = clock.instant();
        final Instant startDate = request.startDate() == null
                ? now.plusSeconds(configService.getMinimumStartDelaySeconds())
                : request.startDate();
        if (!startDate.isAfter(now)) {
            throw new PreconditionFailedException(
                    "Recurly subscription creation requires startDate to be in the future");
        }

        final RecurlyPaymentMethodReference paymentMethod =
                RecurlyPaymentMethodReference.parse(request.paymentMethod().externalId());
        final RecurlySubscriptionParams params = new RecurlySubscriptionParams(request.customer().externalId(),
                paymentMethod.billingInfoId(), planCode(request.plan()), request.quantity(), request.currencyIsoCode(),
                startDate.toString(), paymentMethod.networkTransactionId(),
                operationKey(request.idempotencyKey(), "create"), request.metadata());
        final String subscriptionId = apiClient.createSubscription(params);
        ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
                .success(startedAt)
                .field("subscription_id", subscriptionId)
                .field("plan_id", planCode(request.plan()))
                .field("quantity", Integer.valueOf(request.quantity()))
                .field("currency", request.currencyIsoCode())
                .field("start_at", startDate)
                .field("payment_method_reference", request.paymentMethod().externalId())
                .info(LOG);
        return new BillingSubscriptionRef(BillingPlatform.RECURLY, subscriptionId);
    }

    @Override
    public NormalizedSubscription fetchSubscription(final BillingSubscriptionRef subscription)
            throws BillingException {
        final long startedAt = System.nanoTime();
        try (ConnectorLogContext scope = ConnectorLogContext.open(platform(), "fetch_subscription")) {
            verifyRecurlySubscription(subscription);
            final NormalizedSubscription fetched;
            try {
                fetched = apiClient.fetchSubscription(subscription.externalId());
            } catch (final BillingException e) {
                ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
                        .failure(startedAt, e)
                        .field("subscription_id", subscription.externalId())
                        .warn(LOG);
                throw e;
            }
            ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
                    .success(startedAt)
                    .field("subscription_id", subscription.externalId())
                    .field("subscription_status", fetched == null ? null : fetched.status())
                    .info(LOG);
            return fetched;
        }
    }

    @Override
    public void updateSubscription(final SubscriptionUpdateRequest request) throws BillingException {
        final long startedAt = System.nanoTime();
        try (ConnectorLogContext scope = ConnectorLogContext.open(platform(), "update_subscription")) {
            final String planCode = request.plan() == null ? null : planCode(request.plan());
            try {
                apiClient.updateSubscription(request.subscription().externalId(), planCode, request.quantity(),
                        operationKey(request.idempotencyKey(), "update"));
            } catch (final BillingException e) {
                ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
                        .failure(startedAt, e)
                        .field("subscription_id", externalIdOrNull(request.subscription()))
                        .field("plan_id", planCode)
                        .field("quantity", request.quantity())
                        .warn(LOG);
                throw e;
            }
            ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
                    .success(startedAt)
                    .field("subscription_id", request.subscription().externalId())
                    .field("plan_id", planCode)
                    .field("quantity", request.quantity())
                    .info(LOG);
        }
    }

    @Override
    public void cancelSubscription(final SubscriptionCancelRequest request) throws BillingException {
        final long startedAt = System.nanoTime();
        try (ConnectorLogContext scope = ConnectorLogContext.open(platform(), "cancel_subscription")) {
            // The choice between Recurly's two endpoints is made here and carried no further as a flag, so
            // past this point the destructive one is named `terminate` in the stack trace and the log.
            final CancellationCall call = switch (request.timing()) {
                case AT_PERIOD_END -> (id, key) -> apiClient.cancelAtNextBillDate(id, operationKey(key, "cancel"));
                case IMMEDIATELY -> (id, key) -> apiClient.terminate(id, operationKey(key, "terminate"));
            };
            try {
                call.execute(request.subscription().externalId(), request.idempotencyKey());
            } catch (final BillingException e) {
                ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
                        .failure(startedAt, e)
                        .field("subscription_id", externalIdOrNull(request.subscription()))
                        .field("cancellation_timing", ConnectorLogContext.code(request.timing()))
                        .warn(LOG);
                throw e;
            }
            ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
                    .success(startedAt)
                    .field("subscription_id", request.subscription().externalId())
                    .field("cancellation_timing", ConnectorLogContext.code(request.timing()))
                    .info(LOG);
        }
    }

    /**
     * One of Recurly's two cancellation endpoints, already bound to its own idempotency-key namespace.
     *
     * <p>It lets the choice between them be a switch <em>expression</em>, which is the only form checked for
     * exhaustiveness; one of the two ends a subscription immediately, so a new timing must be a build
     * failure rather than an unnoticed fall-through.</p>
     */
    @FunctionalInterface
    protected interface CancellationCall {
        void execute(String subscriptionId, String idempotencyKey) throws BillingException;
    }

    /**
     * Namespaces the caller's idempotency key by operation, so each stays independently idempotent under
     * retry while remaining distinct from the others.
     *
     * <p>Recurly answers a repeated key with the <em>first</em> response it recorded, and the core issues one
     * key per subscription (the order code) for the whole lifecycle; without this, a cancel would be
     * acknowledged with the stored 201 from the create while Recurly kept billing. The two cancellation
     * timings are namespaced apart as well, {@code cancel} against {@code terminate}.</p>
     */
    protected static String operationKey(final String idempotencyKey, final String operation) {
        return StringUtils.isBlank(idempotencyKey) ? null : idempotencyKey + "/" + operation;
    }

    @Override
    public NormalizedBillingEvent parseWebhook(final RawWebhook raw) throws BillingException {
        try (ConnectorLogContext scope = ConnectorLogContext.open(platform(), "parse_webhook")) {
            return webhookParser.parse(raw);
        }
    }

    /**
     * Recurly fires payment- and invoice-shaped events that never name their subscription, so the
     * invoice or transaction has to be read back to find out which subscriptions it covers — and an
     * invoice can legitimately cover several. The core only calls this for events that arrive without a
     * subscription id, and only after the event id has been claimed, so a redelivery costs no extra
     * round-trip.
     */
    @Override
    public List<String> resolveSubscriptionIds(final NormalizedBillingEvent event) throws BillingException {
        if (event == null) {
            return List.of();
        }
        final long startedAt = System.nanoTime();
        try (ConnectorLogContext scope = ConnectorLogContext.open(platform(), "resolve_webhook")) {
            final Map<String, String> attributes = event.attributes();
            final String resourceType = attributes.get("resourceType");
            final String resourceId = attributes.get("resourceId");
            final List<String> resolved;
            try {
                resolved = apiClient.resolveWebhookSubscriptionIds(resourceType, resourceId);
            } catch (final BillingException e) {
                ConnectorLogEvent.of(EVENT_WEBHOOK_RESOLUTION)
                        .failure(startedAt, e)
                        .field("event_id", event.eventId())
                        .field("resource_type", resourceType)
                        .field("resource_id", resourceId)
                        .warn(LOG);
                throw e;
            }
            if (resolved == null || resolved.isEmpty()) {
                // Not an error: the event simply names nothing this platform can act on. It is still
                // worth a line, because a run of these is how a silent reconciliation hole shows up.
                ConnectorLogEvent.of(EVENT_RECONCILIATION_GAP)
                        .outcome(ConnectorLogEvent.OUTCOME_UNRESOLVED)
                        .durationSince(startedAt)
                        .field("error_class", ConnectorLogEvent.ERROR_CLASS_NONE)
                        .reason("subscription_id_missing")
                        .field("event_id", event.eventId())
                        .field("resource_type", resourceType)
                        .field("resource_id", resourceId)
                        .warn(LOG);
            } else {
                ConnectorLogEvent.of(EVENT_WEBHOOK_RESOLUTION)
                        .success(startedAt)
                        .field("event_id", event.eventId())
                        .field("resource_type", resourceType)
                        .field("resource_id", resourceId)
                        .field("resolved_subscription_count", Integer.valueOf(resolved.size()))
                        .field("resolved_subscription_ids", resolved)
                        .info(LOG);
            }
            return resolved;
        }
    }

    protected void verifyMerchantAccount(final AdyenTokenHandle token)
            throws PreconditionFailedException, ConnectorNotConfiguredException {
        final String configured = configService.getConfiguredAdyenMerchantAccount();
        if (StringUtils.isBlank(configured)) {
            tokenValidationFailure("merchant_account_not_configured", ConnectorLogEvent.ERROR_CLASS_CONFIGURATION,
                    token).error(LOG);
            throw new PreconditionFailedException("Recurly connector has no configured Adyen merchant account "
                    + "(Recurly Config: Adyen Gateway Merchant Account); refusing to import a token "
                    + "without that guarantee");
        }
        if (!configured.equals(token.merchantAccount())) {
            tokenValidationFailure("merchant_account_mismatch", ConnectorLogEvent.ERROR_CLASS_VALIDATION, token)
                    .field("configured_merchant_account", configured)
                    .error(LOG);
            throw new PreconditionFailedException("Recurly connector is bound to Adyen merchant account '" + configured
                    + "' but the token was minted under '" + token.merchantAccount() + "'");
        }
    }

    protected void verifyRecurlyCustomer(final BillingCustomerRef customer) throws PreconditionFailedException {
        if (customer.platform() != BillingPlatform.RECURLY) {
            validationFailure("customer_platform_mismatch", ConnectorLogEvent.ERROR_CLASS_VALIDATION)
                    .field("received_platform", ConnectorLogContext.code(customer.platform()))
                    .warn(LOG);
            throw new PreconditionFailedException("Cannot import an Adyen token into a " + customer.platform()
                    + " customer reference using the Recurly connector");
        }
    }

    protected void verifyRecurlySubscription(final BillingSubscriptionRef subscription)
            throws PreconditionFailedException {
        if (subscription == null) {
            throw new PreconditionFailedException("Cannot fetch a null subscription reference");
        }
        if (subscription.platform() != BillingPlatform.RECURLY) {
            throw new PreconditionFailedException("Cannot fetch a " + subscription.platform()
                    + " subscription reference using the Recurly connector");
        }
    }

    protected void verifySubscriptionModel(final RecurringProcessingModel model) throws PreconditionFailedException {
        if (model != RecurringProcessingModel.SUBSCRIPTION) {
            validationFailure("recurring_model_unsupported", ConnectorLogEvent.ERROR_CLASS_VALIDATION)
                    .field("recurring_model", model)
                    .warn(LOG);
            throw new PreconditionFailedException("Recurly token import supports only SUBSCRIPTION recurring "
                    + "processing, but received " + model);
        }
    }

    protected void verifyTokenOwnership(final BillingCustomerRef customer, final AdyenTokenHandle token)
            throws PreconditionFailedException {
        final String expectedShopperReference = accountCode(customer.externalId());
        if (!StringUtils.equals(expectedShopperReference, token.shopperReference())) {
            tokenValidationFailure("token_ownership_mismatch", ConnectorLogEvent.ERROR_CLASS_VALIDATION, token)
                    .warn(LOG);
            throw new PreconditionFailedException("Adyen token shopperReference does not match the Recurly customer "
                    + "reference; refusing to attach a payment method belonging to another customer");
        }
    }

    protected void verifyNetworkTransactionId(final AdyenTokenHandle token) throws PreconditionFailedException {
        if (!token.hasNetworkTransactionId()) {
            tokenValidationFailure("network_transaction_id_missing", ConnectorLogEvent.ERROR_CLASS_VALIDATION, token)
                    .warn(LOG);
            throw new PreconditionFailedException("Recurly requires a network transaction id for Adyen token import");
        }
    }

    protected void verifyExternalNtidSupport()
            throws PreconditionFailedException, ConnectorNotConfiguredException {
        if (!configService.isExternalNtidFeatureEnabled()) {
            validationFailure("external_ntid_feature_disabled", ConnectorLogEvent.ERROR_CLASS_CONFIGURATION)
                    .warn(LOG);
            throw new PreconditionFailedException("Recurly external-NTID support is not confirmed. Enable "
                    + "'Allow NTIDs in APIs' and 'Enables Backfilling External Tokens', then tick "
                    + "'External Ntid Feature Enabled' in the base store's Recurly Config");
        }
    }

    protected String planCode(final PlanRef plan) {
        return plan.planId();
    }

    protected String accountCode(final String accountId) {
        return StringUtils.removeStart(accountId, "code-");
    }

    /**
     * One event for every refused token import, told apart by {@code reason}, and one line per refusal so
     * that counting the event counts refusals.
     */
    private ConnectorLogEvent tokenValidationFailure(final String reason, final String errorClass,
                                                     final AdyenTokenHandle token) {
        return validationFailure(reason, errorClass)
                .field("token_reference", token == null ? null : token.storedPaymentMethodId())
                .field("merchant_account", token == null ? null : token.merchantAccount())
                .field("network_transaction_id_present",
                        token == null ? null : Boolean.valueOf(token.hasNetworkTransactionId()));
    }

    /**
     * The platform and operation are stated explicitly as a fallback: these guards are {@code protected}
     * and can be called on their own, outside the scope {@link #importAdyenToken} opens. When the scope
     * is open its values win, so the line says the same thing either way.
     */
    private ConnectorLogEvent validationFailure(final String reason, final String errorClass) {
        return ConnectorLogEvent.of(EVENT_TOKEN_IMPORT_VALIDATION_FAILURE)
                .platform(BillingPlatform.RECURLY)
                .operation("import_token")
                .outcome(ConnectorLogEvent.OUTCOME_FAILURE)
                .field("error_class", errorClass)
                .reason(reason);
    }

    private String planCodeOrNull(final PlanRef plan) {
        return plan == null ? null : plan.planId();
    }

    private static String externalIdOrNull(final BillingPaymentMethodRef paymentMethod) {
        return paymentMethod == null ? null : paymentMethod.externalId();
    }

    private static String externalIdOrNull(final BillingSubscriptionRef subscription) {
        return subscription == null ? null : subscription.externalId();
    }
}
