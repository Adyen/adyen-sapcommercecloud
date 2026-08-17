package com.adyen.commerce.connector.recurly;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.adyen.commerce.connector.dto.AdyenTokenHandle;
import com.adyen.commerce.connector.dto.BillingCustomerRef;
import com.adyen.commerce.connector.dto.BillingPaymentMethodRef;
import com.adyen.commerce.connector.dto.BillingSubscriptionRef;
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
import com.adyen.commerce.connector.exception.BillingException;
import com.adyen.commerce.connector.exception.ConnectorNotConfiguredException;
import com.adyen.commerce.connector.exception.PreconditionFailedException;
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
 */
public class RecurlySubscriptionBillingConnector implements SubscriptionBillingConnector {
    private static final Logger LOG = LoggerFactory.getLogger(RecurlySubscriptionBillingConnector.class);
    private static final ConnectorCapabilities CAPABILITIES = new ConnectorCapabilities(
            true,
            false,
            false,
            true,
            false,
            TokenImportStyle.SEPARATE_FIELDS);

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
        return CAPABILITIES;
    }

    @Override
    public String configuredAdyenMerchantAccount() throws ConnectorNotConfiguredException {
        return configService.getConfiguredAdyenMerchantAccount();
    }

    @Override
    public BillingCustomerRef ensureCustomer(final CustomerSyncRequest request) throws BillingException {
        final long startedAt = System.nanoTime();
        final String accountId;
        try {
            accountId = apiClient.ensureCustomer(request.customerId(), request.email(), request.firstName(),
                    request.lastName());
        } catch (final BillingException e) {
            LOG.warn("event=connector_operation platform=RECURLY operation=ensure_customer outcome=failure duration_ms={} "
                            + "error_class={} exception_class={} correlation_id={}", elapsedMillis(startedAt),
                    errorClass(e), e.getClass().getName(), correlationId());
            throw e;
        }
        LOG.info("event=connector_operation platform=RECURLY operation=ensure_customer outcome=success duration_ms={} "
                        + "error_class=none correlation_id={}", elapsedMillis(startedAt), correlationId());
        return new BillingCustomerRef(BillingPlatform.RECURLY, accountId);
    }

    @Override
    public BillingPaymentMethodRef importAdyenToken(final TokenImportRequest request) throws BillingException {
        final long startedAt = System.nanoTime();
        final AdyenTokenHandle token = request.token();
        LOG.info("event=connector_operation platform=RECURLY operation=import_token outcome=started correlation_id={} "
                        + "token_reference={} merchant_account={} network_transaction_id_present={}", correlationId(),
                token.storedPaymentMethodId(), token.merchantAccount(), token.hasNetworkTransactionId());
        verifyRecurlyCustomer(request.customer());
        verifySubscriptionModel(request.model());
        verifyTokenOwnership(request.customer(), token);
        verifyExternalNtidSupport();
        verifyMerchantAccount(token);
        verifyNetworkTransactionId(token);

        final String billingInfoId;
        try {
            billingInfoId = apiClient.importAdyenToken(request.customer().externalId(), token.shopperReference(),
                    token.storedPaymentMethodId(), token.cardMetadata(), request.billingAddress());
        } catch (final BillingException e) {
            LOG.warn("event=connector_operation platform=RECURLY operation=import_token outcome=failure duration_ms={} "
                            + "error_class={} correlation_id={} token_reference={} merchant_account={} "
                            + "network_transaction_id_present={}", elapsedMillis(startedAt), errorClass(e),
                    correlationId(), token.storedPaymentMethodId(), token.merchantAccount(),
                    token.hasNetworkTransactionId());
            throw e;
        }
        LOG.info("event=connector_operation platform=RECURLY operation=import_token outcome=success duration_ms={} "
                        + "error_class=none correlation_id={} token_reference={} billing_info_id={} "
                        + "merchant_account={} network_transaction_id={}", elapsedMillis(startedAt), correlationId(),
                token.storedPaymentMethodId(), billingInfoId, token.merchantAccount(), token.networkTransactionId());
        return new BillingPaymentMethodRef(BillingPlatform.RECURLY,
                RecurlyPaymentMethodReference.encode(billingInfoId, token.networkTransactionId()));
    }

    @Override
    public PlanRef resolvePlan(final PlanResolutionRequest request) throws BillingException {
        final long startedAt = System.nanoTime();
        final PlanRef plan;
        try {
            plan = planResolver.resolve(request);
        } catch (final BillingException e) {
            LOG.warn("event=connector_operation platform=RECURLY operation=resolve_plan outcome=failure duration_ms={} "
                            + "error_class={} exception_class={} correlation_id={} product_code={}",
                    elapsedMillis(startedAt), errorClass(e), e.getClass().getName(), correlationId(),
                    request.productCode());
            throw e;
        }
        LOG.info("event=connector_operation platform=RECURLY operation=resolve_plan outcome=success duration_ms={} "
                        + "error_class=none correlation_id={} product_code={} plan_id={}", elapsedMillis(startedAt),
                correlationId(), request.productCode(), plan.planId());
        return plan;
    }

    @Override
    public BillingSubscriptionRef createSubscription(final SubscriptionCreateRequest request) throws BillingException {
        final long startedAt = System.nanoTime();
        try {
            return createSubscriptionInternal(request, startedAt);
        } catch (final BillingException e) {
            LOG.warn("event=connector_operation platform=RECURLY operation=create_subscription outcome=failure "
                            + "duration_ms={} error_class={} exception_class={} correlation_id={} plan_id={} "
                            + "payment_method_reference={}", elapsedMillis(startedAt), errorClass(e),
                    e.getClass().getName(), correlationId(), planCode(request.plan()),
                    request.paymentMethod().externalId());
            throw e;
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
        LOG.info("event=connector_operation platform=RECURLY operation=create_subscription outcome=success "
                        + "duration_ms={} error_class=none correlation_id={} subscription_id={} plan_id={} quantity={} "
                        + "currency={} start_at={} payment_method_reference={}", elapsedMillis(startedAt), correlationId(),
                subscriptionId, planCode(request.plan()), request.quantity(), request.currencyIsoCode(), startDate,
                request.paymentMethod().externalId());
        return new BillingSubscriptionRef(BillingPlatform.RECURLY, subscriptionId);
    }

    @Override
    public void updateSubscription(final SubscriptionUpdateRequest request) throws BillingException {
        final long startedAt = System.nanoTime();
        final String planCode = request.plan() == null ? null : planCode(request.plan());
        try {
            apiClient.updateSubscription(request.subscription().externalId(), planCode, request.quantity(),
                    operationKey(request.idempotencyKey(), "update"));
        } catch (final BillingException e) {
            LOG.warn("event=connector_operation platform=RECURLY operation=update_subscription outcome=failure "
                            + "duration_ms={} error_class={} exception_class={} correlation_id={} subscription_id={} "
                            + "plan_id={} quantity={}", elapsedMillis(startedAt), errorClass(e), e.getClass().getName(),
                    correlationId(), request.subscription().externalId(), planCode, request.quantity());
            throw e;
        }
        LOG.info("event=connector_operation platform=RECURLY operation=update_subscription outcome=success "
                        + "duration_ms={} error_class=none correlation_id={} subscription_id={} plan_id={} quantity={}",
                elapsedMillis(startedAt), correlationId(), request.subscription().externalId(), planCode,
                request.quantity());
    }

    @Override
    public void cancelSubscription(final SubscriptionCancelRequest request) throws BillingException {
        final long startedAt = System.nanoTime();
        try {
            apiClient.cancelSubscription(request.subscription().externalId(), request.atPeriodEnd(),
                    operationKey(request.idempotencyKey(), "cancel"));
        } catch (final BillingException e) {
            LOG.warn("event=connector_operation platform=RECURLY operation=cancel_subscription outcome=failure "
                            + "duration_ms={} error_class={} exception_class={} correlation_id={} subscription_id={} "
                            + "at_period_end={}", elapsedMillis(startedAt), errorClass(e), e.getClass().getName(),
                    correlationId(), request.subscription().externalId(), request.atPeriodEnd());
            throw e;
        }
        LOG.info("event=connector_operation platform=RECURLY operation=cancel_subscription outcome=success "
                        + "duration_ms={} error_class=none correlation_id={} subscription_id={} at_period_end={}",
                elapsedMillis(startedAt), correlationId(), request.subscription().externalId(), request.atPeriodEnd());
    }

    /**
     * The core issues one idempotency key per subscription (the order code) and replays it for the whole
     * lifecycle, so create, update and cancel would otherwise arrive at Recurly under the same key.
     * Recurly answers a repeated key with the <em>first</em> response it recorded, which would let a
     * cancel be acknowledged with the stored 201 from the create — the local status would flip to
     * CANCELLED while Recurly kept billing. Namespacing by operation keeps each one independently
     * idempotent under retry while making them distinct from each other.
     */
    protected static String operationKey(final String idempotencyKey, final String operation) {
        return StringUtils.isBlank(idempotencyKey) ? null : idempotencyKey + "/" + operation;
    }

    @Override
    public NormalizedBillingEvent parseWebhook(final RawWebhook raw) throws BillingException {
        return webhookParser.parse(raw);
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
        final Map<String, String> attributes = event.attributes();
        final List<String> resolved = apiClient.resolveWebhookSubscriptionIds(attributes.get("resourceType"),
                attributes.get("resourceId"));
        if (resolved == null || resolved.isEmpty()) {
            LOG.warn("event=reconciliation_gap platform=RECURLY operation=resolve_webhook outcome=unresolved "
                    + "error_class=none correlation_id={} event_id={} resource_type={} resource_id={}",
                    correlationId(), event.eventId(), attributes.get("resourceType"), attributes.get("resourceId"));
        } else {
            LOG.info("event=webhook_resolution platform=RECURLY operation=resolve_webhook outcome=success "
                            + "error_class=none correlation_id={} event_id={} resource_type={} resource_id={} "
                            + "resolved_subscription_count={} resolved_subscription_ids={}", correlationId(),
                    event.eventId(), attributes.get("resourceType"), attributes.get("resourceId"), resolved.size(), resolved);
        }
        return resolved;
    }

    protected void verifyMerchantAccount(final AdyenTokenHandle token)
            throws PreconditionFailedException, ConnectorNotConfiguredException {
        final String configured = configService.getConfiguredAdyenMerchantAccount();
        if (StringUtils.isBlank(configured)) {
            logTokenValidationFailure("merchant_account_not_configured", token, "configuration");
            LOG.error("event=merchant_account_mismatch platform=RECURLY operation=import_token outcome=failure "
                            + "error_class=configuration correlation_id={} configured_merchant_account=missing "
                            + "token_merchant_account={}", correlationId(), token.merchantAccount());
            throw new PreconditionFailedException("Recurly connector has no configured Adyen merchant account "
                    + "(recurly.adyenMerchantAccount); refusing to import a token without the R2 guarantee");
        }
        if (!configured.equals(token.merchantAccount())) {
            logTokenValidationFailure("merchant_account_mismatch", token, "validation");
            LOG.error("event=merchant_account_mismatch platform=RECURLY operation=import_token outcome=failure "
                            + "error_class=validation correlation_id={} configured_merchant_account={} "
                            + "token_merchant_account={}", correlationId(), configured, token.merchantAccount());
            throw new PreconditionFailedException("Recurly connector is bound to Adyen merchant account '" + configured
                    + "' but the token was minted under '" + token.merchantAccount() + "'");
        }
    }

    protected void verifyRecurlyCustomer(final BillingCustomerRef customer) throws PreconditionFailedException {
        if (customer.platform() != BillingPlatform.RECURLY) {
            LOG.warn("event=token_import_validation_failure platform=RECURLY operation=import_token outcome=failure "
                            + "error_class=validation reason=customer_platform_mismatch correlation_id={} "
                            + "received_platform={}", correlationId(), customer.platform());
            throw new PreconditionFailedException("Cannot import an Adyen token into a " + customer.platform()
                    + " customer reference using the Recurly connector");
        }
    }

    protected void verifySubscriptionModel(final RecurringProcessingModel model) throws PreconditionFailedException {
        if (model != RecurringProcessingModel.SUBSCRIPTION) {
            LOG.warn("event=token_import_validation_failure platform=RECURLY operation=import_token outcome=failure "
                            + "error_class=validation reason=recurring_model_unsupported correlation_id={} "
                            + "recurring_model={}", correlationId(), model);
            throw new PreconditionFailedException("Recurly token import supports only SUBSCRIPTION recurring "
                    + "processing, but received " + model);
        }
    }

    protected void verifyTokenOwnership(final BillingCustomerRef customer, final AdyenTokenHandle token)
            throws PreconditionFailedException {
        final String expectedShopperReference = accountCode(customer.externalId());
        if (!StringUtils.equals(expectedShopperReference, token.shopperReference())) {
            logTokenValidationFailure("token_ownership_mismatch", token, "validation");
            throw new PreconditionFailedException("Adyen token shopperReference does not match the Recurly customer "
                    + "reference; refusing to attach a payment method belonging to another customer");
        }
    }

    protected void verifyNetworkTransactionId(final AdyenTokenHandle token) throws PreconditionFailedException {
        if (!token.hasNetworkTransactionId()) {
            logTokenValidationFailure("network_transaction_id_missing", token, "validation");
            throw new PreconditionFailedException("Recurly requires a network transaction id for Adyen token import");
        }
    }

    protected void verifyExternalNtidSupport()
            throws PreconditionFailedException, ConnectorNotConfiguredException {
        if (!configService.isExternalNtidFeatureEnabled()) {
            LOG.warn("event=token_import_validation_failure platform=RECURLY operation=import_token outcome=failure "
                    + "error_class=configuration reason=external_ntid_feature_disabled correlation_id={}", correlationId());
            throw new PreconditionFailedException("Recurly external-NTID support is not confirmed. Enable "
                    + "'Allow NTIDs in APIs' and 'Enables Backfilling External Tokens', then set "
                    + "recurly.externalNtidFeatureEnabled=true");
        }
    }

    protected String planCode(final PlanRef plan) {
        return plan.planId();
    }

    protected String accountCode(final String accountId) {
        return StringUtils.removeStart(accountId, "code-");
    }

    private void logTokenValidationFailure(final String reason, final AdyenTokenHandle token,
                                           final String errorClass) {
        LOG.warn("event=token_import_validation_failure platform=RECURLY operation=import_token outcome=failure "
                        + "error_class={} reason={} correlation_id={} token_reference={} merchant_account={} "
                        + "network_transaction_id_present={}", errorClass, reason, correlationId(),
                token == null ? null : token.storedPaymentMethodId(), token == null ? null : token.merchantAccount(),
                token != null && token.hasNetworkTransactionId());
    }

    private static String errorClass(final BillingException error) {
        final String type = error.getClass().getSimpleName();
        if (type.contains("Retryable")) return "remote_retryable";
        if (type.contains("Precondition")) return "validation";
        if (type.contains("Configured")) return "configuration";
        return "remote_terminal";
    }

    private static long elapsedMillis(final long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private static String correlationId() {
        return StringUtils.defaultIfBlank(MDC.get("correlationId"), "none");
    }
}
