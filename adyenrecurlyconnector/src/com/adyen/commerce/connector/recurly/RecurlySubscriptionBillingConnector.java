package com.adyen.commerce.connector.recurly;

import java.time.Clock;
import java.time.Instant;

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
public class RecurlySubscriptionBillingConnector implements SubscriptionBillingConnector
{
    private static final ConnectorCapabilities CAPABILITIES = new ConnectorCapabilities(
            true,  // requiresNetworkTransactionId
            false, // supportsImmediateStart — Adyen gateway-token import is future-dated
            false, // supportsPause — can be added once the SPI pause request maps cleanly to Recurly cycles
            true,  // requiresPreConfiguredPlan
            false, // liveTokenValidationOnImport — token validation happens when Recurly/gateway uses the reference
            TokenImportStyle.SEPARATE_FIELDS);

    private RecurlyApiClient apiClient;
    private RecurlyConfigService configService;
    private RecurlyPlanResolver planResolver;
    private RecurlyWebhookParser webhookParser;
    private Clock clock = Clock.systemUTC();

    @Override
    public BillingPlatform platform()
    {
        return BillingPlatform.RECURLY;
    }

    @Override
    public ConnectorCapabilities capabilities()
    {
        return CAPABILITIES;
    }

    @Override
    public String configuredAdyenMerchantAccount()
    {
        return configService.getConfiguredAdyenMerchantAccount();
    }

    @Override
    public BillingCustomerRef ensureCustomer(final CustomerSyncRequest request) throws BillingException
    {
        final String accountId = apiClient.ensureCustomer(request.customerId(), request.email(), request.firstName(),
                request.lastName());
        return new BillingCustomerRef(BillingPlatform.RECURLY, accountId);
    }

    @Override
    public BillingPaymentMethodRef importAdyenToken(final TokenImportRequest request) throws BillingException
    {
        final AdyenTokenHandle token = request.token();
        verifyMerchantAccount(token);
        verifyNetworkTransactionId(token);

        final String billingInfoId = apiClient.importAdyenToken(request.customer().externalId(),
                token.shopperReference(), token.storedPaymentMethodId(), token.cardMetadata(), request.billingAddress());
        return new BillingPaymentMethodRef(BillingPlatform.RECURLY,
                RecurlyPaymentMethodReference.encode(billingInfoId, token.networkTransactionId()));
    }

    @Override
    public PlanRef resolvePlan(final PlanResolutionRequest request) throws BillingException
    {
        return planResolver.resolve(request);
    }

    @Override
    public BillingSubscriptionRef createSubscription(final SubscriptionCreateRequest request) throws BillingException
    {
        final Instant now = clock.instant();
        final Instant startDate = request.startDate() == null
                ? now.plusSeconds(configService.getMinimumStartDelaySeconds())
                : request.startDate();
        if (!startDate.isAfter(now))
        {
            throw new PreconditionFailedException("Recurly subscription creation requires startDate to be in the future");
        }

        final RecurlyPaymentMethodReference paymentMethod =
                RecurlyPaymentMethodReference.parse(request.paymentMethod().externalId());
        final RecurlySubscriptionParams params = new RecurlySubscriptionParams(request.customer().externalId(),
                paymentMethod.billingInfoId(), planCode(request.plan()), request.quantity(), request.currencyIsoCode(),
                startDate.toString(), paymentMethod.networkTransactionId(), request.idempotencyKey(),
                request.metadata());
        final String subscriptionId = apiClient.createSubscription(params);
        return new BillingSubscriptionRef(BillingPlatform.RECURLY, subscriptionId);
    }

    @Override
    public void updateSubscription(final SubscriptionUpdateRequest request) throws BillingException
    {
        final String planCode = request.plan() == null ? null : planCode(request.plan());
        apiClient.updateSubscription(request.subscription().externalId(), planCode, request.quantity(),
                request.idempotencyKey());
    }

    @Override
    public void cancelSubscription(final SubscriptionCancelRequest request) throws BillingException
    {
        apiClient.cancelSubscription(request.subscription().externalId(), request.atPeriodEnd(), request.idempotencyKey());
    }

    @Override
    public NormalizedBillingEvent parseWebhook(final RawWebhook raw) throws BillingException
    {
        return webhookParser.parse(raw);
    }

    protected void verifyMerchantAccount(final AdyenTokenHandle token) throws PreconditionFailedException
    {
        final String configured = configService.getConfiguredAdyenMerchantAccount();
        if (StringUtils.isBlank(configured))
        {
            throw new PreconditionFailedException("Recurly connector has no configured Adyen merchant account "
                    + "(recurly.adyenMerchantAccount); refusing to import a token without the R2 guarantee");
        }
        if (!configured.equals(token.merchantAccount()))
        {
            throw new PreconditionFailedException("Recurly connector is bound to Adyen merchant account '" + configured
                    + "' but the token was minted under '" + token.merchantAccount() + "'");
        }
    }

    protected void verifyNetworkTransactionId(final AdyenTokenHandle token) throws PreconditionFailedException
    {
        if (!token.hasNetworkTransactionId())
        {
            throw new PreconditionFailedException("Recurly requires a network transaction id for Adyen token import");
        }
    }

    protected String planCode(final PlanRef plan)
    {
        return plan.planId();
    }

    public void setApiClient(final RecurlyApiClient apiClient)
    {
        this.apiClient = apiClient;
    }

    public void setConfigService(final RecurlyConfigService configService)
    {
        this.configService = configService;
    }

    public void setPlanResolver(final RecurlyPlanResolver planResolver)
    {
        this.planResolver = planResolver;
    }

    public void setWebhookParser(final RecurlyWebhookParser webhookParser)
    {
        this.webhookParser = webhookParser;
    }

    public void setClock(final Clock clock)
    {
        this.clock = clock;
    }
}
