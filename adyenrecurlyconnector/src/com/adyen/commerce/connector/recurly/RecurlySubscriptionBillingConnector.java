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
 * Recurly adapter of the {@link SubscriptionBillingConnector} SPI. Every entry point opens a
 * {@link ConnectorLogContext}, so the API client and transport log lines carry the operation name.
 */
public class RecurlySubscriptionBillingConnector implements SubscriptionBillingConnector {
    private static final Logger LOG = LoggerFactory.getLogger(RecurlySubscriptionBillingConnector.class);

    private static final String EVENT_CONNECTOR_OPERATION = "connector_operation";
    private static final String EVENT_TOKEN_IMPORT_VALIDATION_FAILURE = "token_import_validation_failure";
    private static final String EVENT_WEBHOOK_RESOLUTION = "webhook_resolution";
    private static final String EVENT_RECONCILIATION_GAP = "reconciliation_gap";

    private static final String F_SUBSCRIPTION_ID = "subscription_id";
    private static final String F_PLAN_ID = "plan_id";
    private static final String F_QUANTITY = "quantity";
    private static final String F_BILLING_INFO_ID = "billing_info_id";
    private static final String F_EXTERNAL_ID = "external_id";
    private static final String F_TOKEN_REFERENCE = "token_reference";
    private static final String F_MERCHANT_ACCOUNT = "merchant_account";
    private static final String F_NTID_PRESENT = "network_transaction_id_present";
    private static final String F_EVENT_ID = "event_id";
    private static final String F_RESOURCE_TYPE = "resource_type";
    private static final String F_RESOURCE_ID = "resource_id";

    /** Payment-method change and enrollment depend on configuration and are added per call. */
    private static final ConnectorCapabilities BASE_CAPABILITIES = new ConnectorCapabilities(
            true,
            false,
            false,
            true,
            false,
            TokenImportStyle.SEPARATE_FIELDS,
            PaymentMethodChangeSupport.NONE,
            PaymentMethodEnrollmentSupport.NONE);

    private RecurlyApiClient apiClient;
    private RecurlyConfigService configService;
    private RecurlyPlanResolver planResolver;
    private RecurlyWebhookParser webhookParser;
    private Clock clock = Clock.systemUTC();

    @Override
    public BillingPlatform platform() {
        return BillingPlatform.RECURLY;
    }

    @Override
    public ConnectorCapabilities capabilities() {
        // Non-throwing accessors: an unconfigured store offers nothing rather than failing checkout.
        // SUBSCRIPTION scope because Recurly pins a billing info to one subscription; a vaulted Adyen card
        // needs external-token import, which carries the network transaction id.
        final PaymentMethodChangeSupport change = configService.isPaymentMethodChangeEnabledOrFalse()
                ? new PaymentMethodChangeSupport(PaymentMethodChangeScope.SUBSCRIPTION,
                        configService.isExternalNtidFeatureEnabledOrFalse()
                                ? Set.of(PaymentMethodSource.ALREADY_ON_PLATFORM,
                                        PaymentMethodSource.ADYEN_VAULTED_TOKEN)
                                : Set.of(PaymentMethodSource.ALREADY_ON_PLATFORM))
                : PaymentMethodChangeSupport.NONE;

        // Recurly's hosted pages edit only the primary billing info.
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

    /**
     * Makes the chosen card the account's default when the store asks for it. Never fatal: the subscription
     * already bills the card, so a failure here only leaves the account default stale.
     */
    protected void promoteToPrimaryIfConfigured(final PaymentMethodChangeRequest request,
                                                final String billingInfoId) {
        try {
            if (!configService.isPromoteChosenCardToPrimaryEnabled()) {
                return;
            }
            // Promotion retries collection on unpaid invoices, so it is not repeated for a primary card.
            if (isAlreadyPrimary(request.customer().externalId(), billingInfoId)) {
                return;
            }
            apiClient.promoteBillingInfoToPrimary(request.customer().externalId(), billingInfoId,
                    operationKey(request.idempotencyKey(), "primary"));
        } catch (final BillingException | RuntimeException e) {
            ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
                    .field(F_BILLING_INFO_ID, billingInfoId)
                    .field(F_EXTERNAL_ID, request.customer().externalId())
                    .field("error_class", e.getClass().getSimpleName())
                    .warn(LOG);
        }
    }

    /** Whether the account already bills this card by default, so promoting it would change nothing. */
    protected boolean isAlreadyPrimary(final String accountId, final String billingInfoId)
            throws BillingException {
        return apiClient.listBillingInfos(accountId).stream()
                .anyMatch(method -> StringUtils.equals(billingInfoId, method.id())
                        && method.defaultForCustomer());
    }

    @Override
    public String listedPaymentMethodId(final String externalPaymentMethodId) {
        return RecurlyPaymentMethodReference.billingInfoIdOf(externalPaymentMethodId);
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
        try (ConnectorLogContext ignored = ConnectorLogContext.open(platform(), "change_payment_method")) {
            // Re-checked: configuration may change between rendering the page and posting the form.
            if (!configService.isPaymentMethodChangeEnabledOrFalse()) {
                throw new CapabilityUnsupportedException("Recurly payment-method changes are not enabled for "
                        + "this store; they need Subscriber Wallet and the store's own switch for it");
            }

            final String billingInfoId = switch (request.choice()) {
                case PaymentMethodChoice.AlreadyOnPlatform onPlatform -> onPlatform.platformPaymentMethodId();
                // importAdyenToken applies all token guards and reuses an already imported billing info.
                case PaymentMethodChoice.AdyenVaultedToken vaulted -> {
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
                promoteToPrimaryIfConfigured(request, billingInfoId);
            } catch (final BillingException e) {
                ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
                        .failure(startedAt, e)
                        .field(F_SUBSCRIPTION_ID, subscriptionId)
                        .field(F_BILLING_INFO_ID, billingInfoId)
                        .warn(LOG);
                throw e;
            }
            ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
                    .success(startedAt)
                    .field(F_SUBSCRIPTION_ID, subscriptionId)
                    .field(F_BILLING_INFO_ID, billingInfoId)
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
        try (ConnectorLogContext ignored = ConnectorLogContext.open(platform(), "ensure_customer")) {
            final String accountId;
            try {
                accountId = apiClient.ensureCustomer(request.customerId(), request.email(), request.firstName(),
                        request.lastName());
            } catch (final BillingException e) {
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
        try (ConnectorLogContext ignored = ConnectorLogContext.open(platform(), "import_token")) {
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
                        .field(F_EXTERNAL_ID, request.customer().externalId())
                        .field(F_TOKEN_REFERENCE, token.storedPaymentMethodId())
                        .field(F_MERCHANT_ACCOUNT, token.merchantAccount())
                        .field(F_NTID_PRESENT, token.hasNetworkTransactionId())
                        .warn(LOG);
                throw e;
            }
            // NTID presence only, never its value.
            ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
                    .success(startedAt)
                    .field(F_EXTERNAL_ID, request.customer().externalId())
                    .field(F_TOKEN_REFERENCE, token.storedPaymentMethodId())
                    .field(F_BILLING_INFO_ID, billingInfoId)
                    .field(F_MERCHANT_ACCOUNT, token.merchantAccount())
                    .field(F_NTID_PRESENT, token.hasNetworkTransactionId())
                    .info(LOG);
            return new BillingPaymentMethodRef(BillingPlatform.RECURLY,
                    RecurlyPaymentMethodReference.encode(billingInfoId, token.networkTransactionId()));
        }
    }

    @Override
    public PlanRef resolvePlan(final PlanResolutionRequest request) throws BillingException {
        final long startedAt = System.nanoTime();
        try (ConnectorLogContext ignored = ConnectorLogContext.open(platform(), "resolve_plan")) {
            final PlanRef plan;
            try {
                plan = planResolver.resolve(request);
            } catch (final BillingException e) {
                ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
                        .failure(startedAt, e)
                        .field("product_code", request.productCode())
                        .field("base_store", request.baseStoreUid())
                        .warn(LOG);
                throw e;
            }
            ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
                    .success(startedAt)
                    .field("product_code", request.productCode())
                    .field("base_store", request.baseStoreUid())
                    .field(F_PLAN_ID, planCodeOrNull(plan))
                    .info(LOG);
            return plan;
        }
    }

    @Override
    public BillingSubscriptionRef createSubscription(final SubscriptionCreateRequest request) throws BillingException {
        final long startedAt = System.nanoTime();
        try (ConnectorLogContext ignored = ConnectorLogContext.open(platform(), "create_subscription")) {
            try {
                return createSubscriptionInternal(request, startedAt);
            } catch (final BillingException e) {
                // Null-tolerant: an NPE here would hide the real cause.
                ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
                        .failure(startedAt, e)
                        .field(F_PLAN_ID, planCodeOrNull(request.plan()))
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
                .field(F_SUBSCRIPTION_ID, subscriptionId)
                .field(F_PLAN_ID, planCode(request.plan()))
                .field(F_QUANTITY, request.quantity())
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
        try (ConnectorLogContext ignored = ConnectorLogContext.open(platform(), "fetch_subscription")) {
            verifyRecurlySubscription(subscription);
            final NormalizedSubscription fetched;
            try {
                fetched = apiClient.fetchSubscription(subscription.externalId());
            } catch (final BillingException e) {
                ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
                        .failure(startedAt, e)
                        .field(F_SUBSCRIPTION_ID, subscription.externalId())
                        .warn(LOG);
                throw e;
            }
            ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
                    .success(startedAt)
                    .field(F_SUBSCRIPTION_ID, subscription.externalId())
                    .field("subscription_status", fetched == null ? null : fetched.status())
                    .info(LOG);
            return fetched;
        }
    }

    @Override
    public void updateSubscription(final SubscriptionUpdateRequest request) throws BillingException {
        final long startedAt = System.nanoTime();
        try (ConnectorLogContext ignored = ConnectorLogContext.open(platform(), "update_subscription")) {
            final String planCode = request.plan() == null ? null : planCode(request.plan());
            try {
                apiClient.updateSubscription(request.subscription().externalId(), planCode, request.quantity(),
                        operationKey(request.idempotencyKey(), "update"));
            } catch (final BillingException e) {
                ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
                        .failure(startedAt, e)
                        .field(F_SUBSCRIPTION_ID, externalIdOrNull(request.subscription()))
                        .field(F_PLAN_ID, planCode)
                        .field(F_QUANTITY, request.quantity())
                        .warn(LOG);
                throw e;
            }
            ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
                    .success(startedAt)
                    .field(F_SUBSCRIPTION_ID, request.subscription().externalId())
                    .field(F_PLAN_ID, planCode)
                    .field(F_QUANTITY, request.quantity())
                    .info(LOG);
        }
    }

    @Override
    public void cancelSubscription(final SubscriptionCancelRequest request) throws BillingException {
        final long startedAt = System.nanoTime();
        try (ConnectorLogContext ignored = ConnectorLogContext.open(platform(), "cancel_subscription")) {
            final CancellationCall call = switch (request.timing()) {
                case AT_PERIOD_END -> (id, key) -> apiClient.cancelAtNextBillDate(id, operationKey(key, "cancel"));
                case IMMEDIATELY -> (id, key) -> apiClient.terminate(id, operationKey(key, "terminate"));
            };
            try {
                call.execute(request.subscription().externalId(), request.idempotencyKey());
            } catch (final BillingException e) {
                ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
                        .failure(startedAt, e)
                        .field(F_SUBSCRIPTION_ID, externalIdOrNull(request.subscription()))
                        .field("cancellation_timing", ConnectorLogContext.code(request.timing()))
                        .warn(LOG);
                throw e;
            }
            ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
                    .success(startedAt)
                    .field(F_SUBSCRIPTION_ID, request.subscription().externalId())
                    .field("cancellation_timing", ConnectorLogContext.code(request.timing()))
                    .info(LOG);
        }
    }

    /** One of Recurly's two cancellation endpoints; a switch expression keeps the choice exhaustive. */
    @FunctionalInterface
    protected interface CancellationCall {
        void execute(String subscriptionId, String idempotencyKey) throws BillingException;
    }

    /**
     * Namespaces the idempotency key by operation. The core uses one key per subscription, and Recurly replays
     * the first response recorded for a key, so an un-namespaced cancel would get the create's 201 back.
     */
    protected static String operationKey(final String idempotencyKey, final String operation) {
        return StringUtils.isBlank(idempotencyKey) ? null : idempotencyKey + "/" + operation;
    }

    @Override
    public NormalizedBillingEvent parseWebhook(final RawWebhook raw) throws BillingException {
        try (ConnectorLogContext ignored = ConnectorLogContext.open(platform(), "parse_webhook")) {
            return webhookParser.parse(raw);
        }
    }

    /** Payment and invoice events do not name their subscriptions, so the resource is read back. */
    @Override
    public List<String> resolveSubscriptionIds(final NormalizedBillingEvent event) throws BillingException {
        if (event == null) {
            return List.of();
        }
        final long startedAt = System.nanoTime();
        try (ConnectorLogContext ignored = ConnectorLogContext.open(platform(), "resolve_webhook")) {
            final Map<String, String> attributes = event.attributes();
            final String resourceType = attributes.get("resourceType");
            final String resourceId = attributes.get("resourceId");
            final List<String> resolved;
            try {
                resolved = apiClient.resolveWebhookSubscriptionIds(resourceType, resourceId);
            } catch (final BillingException e) {
                ConnectorLogEvent.of(EVENT_WEBHOOK_RESOLUTION)
                        .failure(startedAt, e)
                        .field(F_EVENT_ID, event.eventId())
                        .field(F_RESOURCE_TYPE, resourceType)
                        .field(F_RESOURCE_ID, resourceId)
                        .warn(LOG);
                throw e;
            }
            if (resolved == null || resolved.isEmpty()) {
                // Not an error, but a run of these reveals a reconciliation gap.
                ConnectorLogEvent.of(EVENT_RECONCILIATION_GAP)
                        .outcome(ConnectorLogEvent.OUTCOME_UNRESOLVED)
                        .durationSince(startedAt)
                        .field("error_class", ConnectorLogEvent.ERROR_CLASS_NONE)
                        .reason("subscription_id_missing")
                        .field(F_EVENT_ID, event.eventId())
                        .field(F_RESOURCE_TYPE, resourceType)
                        .field(F_RESOURCE_ID, resourceId)
                        .warn(LOG);
            } else {
                ConnectorLogEvent.of(EVENT_WEBHOOK_RESOLUTION)
                        .success(startedAt)
                        .field(F_EVENT_ID, event.eventId())
                        .field(F_RESOURCE_TYPE, resourceType)
                        .field(F_RESOURCE_ID, resourceId)
                        .field("resolved_subscription_count", resolved.size())
                        .field("resolved_subscription_ids", resolved)
                        .info(LOG);
            }
            return resolved;
        }
    }

    protected void verifyMerchantAccount(final AdyenTokenHandle token) throws PreconditionFailedException {
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

    private ConnectorLogEvent tokenValidationFailure(final String reason, final String errorClass,
                                                     final AdyenTokenHandle token) {
        return validationFailure(reason, errorClass)
                .field(F_TOKEN_REFERENCE, token == null ? null : token.storedPaymentMethodId())
                .field(F_MERCHANT_ACCOUNT, token == null ? null : token.merchantAccount())
                .field(F_NTID_PRESENT, token == null ? null : token.hasNetworkTransactionId());
    }

    /** Platform and operation for guards called outside the scope {@link #importAdyenToken} opens. */
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

    public void setApiClient(final RecurlyApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public void setConfigService(final RecurlyConfigService configService) {
        this.configService = configService;
    }

    public void setPlanResolver(final RecurlyPlanResolver planResolver) {
        this.planResolver = planResolver;
    }

    public void setWebhookParser(final RecurlyWebhookParser webhookParser) {
        this.webhookParser = webhookParser;
    }

    void setClock(final Clock clock) {
        this.clock = clock;
    }
}
