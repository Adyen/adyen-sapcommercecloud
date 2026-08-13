package com.adyen.commerce.connector.recurly;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

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
    public String configuredAdyenMerchantAccount() {
        return configService.getConfiguredAdyenMerchantAccount();
    }

    @Override
    public BillingCustomerRef ensureCustomer(final CustomerSyncRequest request) throws BillingException {
        final String accountId = apiClient.ensureCustomer(request.customerId(), request.email(), request.firstName(),
                request.lastName());
        return new BillingCustomerRef(BillingPlatform.RECURLY, accountId);
    }

    @Override
    public BillingPaymentMethodRef importAdyenToken(final TokenImportRequest request) throws BillingException {
        final AdyenTokenHandle token = request.token();
        verifyRecurlyCustomer(request.customer());
        verifySubscriptionModel(request.model());
        verifyTokenOwnership(request.customer(), token);
        verifyExternalNtidSupport();
        verifyMerchantAccount(token);
        verifyNetworkTransactionId(token);

        final String billingInfoId = apiClient.importAdyenToken(request.customer().externalId(),
                token.shopperReference(), token.storedPaymentMethodId(), token.cardMetadata(),
                request.billingAddress());
        return new BillingPaymentMethodRef(BillingPlatform.RECURLY,
                RecurlyPaymentMethodReference.encode(billingInfoId, token.networkTransactionId()));
    }

    @Override
    public PlanRef resolvePlan(final PlanResolutionRequest request) throws BillingException {
        return planResolver.resolve(request);
    }

    @Override
    public BillingSubscriptionRef createSubscription(final SubscriptionCreateRequest request) throws BillingException {
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
        return new BillingSubscriptionRef(BillingPlatform.RECURLY, subscriptionId);
    }

    @Override
    public void updateSubscription(final SubscriptionUpdateRequest request) throws BillingException {
        final String planCode = request.plan() == null ? null : planCode(request.plan());
        apiClient.updateSubscription(request.subscription().externalId(), planCode, request.quantity(),
                operationKey(request.idempotencyKey(), "update"));
    }

    @Override
    public void cancelSubscription(final SubscriptionCancelRequest request) throws BillingException {
        apiClient.cancelSubscription(request.subscription().externalId(), request.atPeriodEnd(),
                operationKey(request.idempotencyKey(), "cancel"));
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
        return apiClient.resolveWebhookSubscriptionIds(attributes.get("resourceType"), attributes.get("resourceId"));
    }

    protected void verifyMerchantAccount(final AdyenTokenHandle token) throws PreconditionFailedException {
        final String configured = configService.getConfiguredAdyenMerchantAccount();
        if (StringUtils.isBlank(configured)) {
            throw new PreconditionFailedException("Recurly connector has no configured Adyen merchant account "
                    + "(Recurly Config: Adyen Gateway Merchant Account); refusing to import a token "
                    + "without that guarantee");
        }
        if (!configured.equals(token.merchantAccount())) {
            throw new PreconditionFailedException("Recurly connector is bound to Adyen merchant account '" + configured
                    + "' but the token was minted under '" + token.merchantAccount() + "'");
        }
    }

    protected void verifyRecurlyCustomer(final BillingCustomerRef customer) throws PreconditionFailedException {
        if (customer.platform() != BillingPlatform.RECURLY) {
            throw new PreconditionFailedException("Cannot import an Adyen token into a " + customer.platform()
                    + " customer reference using the Recurly connector");
        }
    }

    protected void verifySubscriptionModel(final RecurringProcessingModel model) throws PreconditionFailedException {
        if (model != RecurringProcessingModel.SUBSCRIPTION) {
            throw new PreconditionFailedException("Recurly token import supports only SUBSCRIPTION recurring "
                    + "processing, but received " + model);
        }
    }

    protected void verifyTokenOwnership(final BillingCustomerRef customer, final AdyenTokenHandle token)
            throws PreconditionFailedException {
        final String expectedShopperReference = accountCode(customer.externalId());
        if (!StringUtils.equals(expectedShopperReference, token.shopperReference())) {
            throw new PreconditionFailedException("Adyen token shopperReference does not match the Recurly customer "
                    + "reference; refusing to attach a payment method belonging to another customer");
        }
    }

    protected void verifyNetworkTransactionId(final AdyenTokenHandle token) throws PreconditionFailedException {
        if (!token.hasNetworkTransactionId()) {
            throw new PreconditionFailedException("Recurly requires a network transaction id for Adyen token import");
        }
    }

    protected void verifyExternalNtidSupport() throws BillingException {
        if (!configService.isExternalNtidFeatureEnabled()) {
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
}