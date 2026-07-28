package com.adyen.commerce.connector.recurly.client.impl;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.adyen.commerce.connector.dto.CardMetadata;
import com.adyen.commerce.connector.dto.BillingAddress;
import com.adyen.commerce.connector.exception.BillingException;
import com.adyen.commerce.connector.exception.PreconditionFailedException;
import com.adyen.commerce.connector.exception.RetryableBillingException;
import com.adyen.commerce.connector.exception.TerminalBillingException;
import com.adyen.commerce.connector.recurly.client.RecurlyApiClient;
import com.adyen.commerce.connector.recurly.client.RecurlySubscriptionParams;
import com.adyen.commerce.connector.recurly.config.RecurlyConfigService;
import com.adyen.commerce.connector.recurly.http.RecurlyHttpClient;
import com.adyen.commerce.connector.recurly.http.RecurlyHttpResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Default Recurly client. It intentionally keeps vendor JSON translation inside the adapter and
 * surfaces only normalized {@link BillingException} failures to the SPI layer.
 */
public class DefaultRecurlyApiClient implements RecurlyApiClient
{
    private final ObjectMapper objectMapper = new ObjectMapper();

    private RecurlyHttpClient httpClient;
    private RecurlyConfigService configService;

    @Override
    public String ensureCustomer(final String customerId, final String email, final String firstName,
                                 final String lastName) throws BillingException
    {
        final String accountId = accountCodeId(customerId);
        final RecurlyHttpResponse existing = httpClient.get(url("/accounts/" + pathSegment(accountId)), authHeader(),
                acceptHeader());
        if (existing.statusCode() == 200)
        {
            return accountId;
        }
        if (existing.statusCode() != 404)
        {
            throw toBillingException(existing, "retrieve account");
        }

        final ObjectNode request = objectMapper.createObjectNode();
        putIfNotBlank(request, "code", customerId);
        putIfNotBlank(request, "email", email);
        putIfNotBlank(request, "first_name", firstName);
        putIfNotBlank(request, "last_name", lastName);

        final RecurlyHttpResponse response = httpClient.post(url("/accounts"), authHeader(), acceptHeader(),
                writeJson(request), customerId);
        requireSuccess(response, "create account");
        return accountId;
    }

    @Override
    public String importAdyenToken(final String accountId, final String shopperReference,
                                   final String storedPaymentMethodId, final CardMetadata card,
                                   final BillingAddress billingAddress) throws BillingException
    {
        final ObjectNode request = objectMapper.createObjectNode();
        putIfNotBlank(request, "gateway_code", configService.getGatewayCode());

        final ObjectNode gatewayAttributes = request.putObject("gateway_attributes");
        putIfNotBlank(gatewayAttributes, "account_reference", shopperReference);

        final ArrayNode references = request.putArray("payment_gateway_references");
        final ObjectNode reference = references.addObject();
        putIfNotBlank(reference, "token", storedPaymentMethodId);

        if (card != null)
        {
            putIfNotBlank(request, "last_four", card.last4());
            addExpiry(request, card.expiry());
        }
        addBillingAddress(request, billingAddress);

        final RecurlyHttpResponse response = httpClient.put(
                url("/accounts/" + pathSegment(accountId) + "/billing_info"), authHeader(), acceptHeader(),
                writeJson(request), shopperReference + "/" + storedPaymentMethodId);
        requireSuccess(response, "import Adyen token as primary billing info");
        return readId(response.body());
    }

    @Override
    public String createSubscription(final RecurlySubscriptionParams params) throws BillingException
    {
        final ObjectNode request = objectMapper.createObjectNode();

        final ObjectNode account = request.putObject("account");
        putIfNotBlank(account, "id", params.accountId());

        putIfNotBlank(request, "billing_info_id", params.billingInfoId());
        putIfNotBlank(request, "plan_code", params.planCode());
        request.put("quantity", Math.max(1, params.quantity()));
        putIfNotBlank(request, "currency", params.currencyIsoCode());
        putIfNotBlank(request, "starts_at", params.startsAt());
        putIfNotBlank(request, "network_transaction_id", params.networkTransactionId());

        if (params.metadata() != null && !params.metadata().isEmpty())
        {
            final ArrayNode customFields = request.putArray("custom_fields");
            for (final Map.Entry<String, String> entry : params.metadata().entrySet())
            {
                if (StringUtils.isNotBlank(entry.getKey()) && StringUtils.isNotBlank(entry.getValue()))
                {
                    final ObjectNode customField = customFields.addObject();
                    customField.put("name", entry.getKey());
                    customField.put("value", entry.getValue());
                }
            }
        }

        final RecurlyHttpResponse response = httpClient.post(url("/subscriptions"), authHeader(), acceptHeader(),
                writeJson(request), params.subscriptionId());
        requireSuccess(response, "create subscription");
        return readSubscriptionId(response.body());
    }

    @Override
    public void updateSubscription(final String subscriptionId, final String planCode, final Integer quantity,
                                   final String idempotencyKey) throws BillingException
    {
        if (StringUtils.isBlank(planCode) && quantity == null)
        {
            throw new PreconditionFailedException(
                    "updateSubscription called with nothing to change for subscription '" + subscriptionId + "'");
        }

        final ObjectNode request = objectMapper.createObjectNode();
        putIfNotBlank(request, "plan_code", planCode);
        if (quantity != null)
        {
            request.put("quantity", quantity.intValue());
        }

        final RecurlyHttpResponse response = httpClient.post(
                url("/subscriptions/" + pathSegment(subscriptionId) + "/change"), authHeader(), acceptHeader(),
                writeJson(request), idempotencyKey);
        requireSuccess(response, "update subscription");
    }

    @Override
    public void cancelSubscription(final String subscriptionId, final boolean atPeriodEnd, final String idempotencyKey)
            throws BillingException
    {
        final String subscriptionPath = "/subscriptions/" + pathSegment(subscriptionId);
        if (atPeriodEnd)
        {
            final ObjectNode request = objectMapper.createObjectNode();
            request.put("timeframe", "bill_date");
            final RecurlyHttpResponse response = httpClient.put(url(subscriptionPath + "/cancel"), authHeader(),
                    acceptHeader(), writeJson(request), idempotencyKey);
            requireSuccess(response, "cancel subscription at next bill date");
        }
        else
        {
            final RecurlyHttpResponse response = httpClient.delete(url(subscriptionPath), authHeader(), acceptHeader(),
                    idempotencyKey);
            requireSuccess(response, "terminate subscription");
        }
    }

    protected String authHeader() throws BillingException
    {
        final String encoded = Base64.getEncoder()
                .encodeToString((configService.getApiKey() + ":").getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    protected String acceptHeader()
    {
        return "application/vnd.recurly." + configService.getApiVersion() + "+json";
    }

    protected String url(final String path) throws BillingException
    {
        return configService.getApiBaseUrl() + (path.startsWith("/") ? path : "/" + path);
    }

    protected void requireSuccess(final RecurlyHttpResponse response, final String action) throws BillingException
    {
        if (!response.isSuccess())
        {
            throw toBillingException(response, action);
        }
    }

    protected BillingException toBillingException(final RecurlyHttpResponse response, final String action)
    {
        final String detail = extractError(response.body());
        final String message = "Recurly " + action + " failed (HTTP " + response.statusCode() + ")"
                + (detail == null ? "" : ": " + detail);
        if (response.statusCode() == 408 || response.statusCode() == 409 || response.statusCode() == 429
                || response.statusCode() >= 500 || StringUtils.containsIgnoreCase(detail, "simultaneous_request"))
        {
            return new RetryableBillingException(message);
        }
        return new TerminalBillingException(message);
    }

    protected String extractError(final String body)
    {
        if (StringUtils.isBlank(body))
        {
            return null;
        }
        try
        {
            final JsonNode node = objectMapper.readTree(body);
            final String message = node.path("message").asText(node.path("error").path("message").asText(null));
            final String type = node.path("type").asText(node.path("error").path("type").asText(null));
            if (message == null && type == null)
            {
                return null;
            }
            return (type == null ? "" : "[" + type + "] ") + StringUtils.defaultString(message);
        }
        catch (final IOException e)
        {
            return null;
        }
    }

    protected String readId(final String body) throws BillingException
    {
        try
        {
            final JsonNode id = objectMapper.readTree(body).path("id");
            if (id.isMissingNode() || StringUtils.isBlank(id.asText(null)))
            {
                throw new TerminalBillingException("Recurly response missing id");
            }
            return id.asText();
        }
        catch (final IOException e)
        {
            throw new TerminalBillingException("Malformed Recurly response: " + e.getMessage());
        }
    }

    /**
     * JSON subscription webhooks identify subscriptions by UUID. Persist the API-compatible
     * {@code uuid-...} identifier so outbound lifecycle calls and inbound reconciliation use the same key.
     */
    protected String readSubscriptionId(final String body) throws BillingException
    {
        try
        {
            final JsonNode response = objectMapper.readTree(body);
            final String uuid = response.path("uuid").asText(null);
            if (StringUtils.isNotBlank(uuid))
            {
                return "uuid-" + uuid;
            }
            final String id = response.path("id").asText(null);
            if (StringUtils.isNotBlank(id))
            {
                return id;
            }
            throw new TerminalBillingException("Recurly subscription response missing id and uuid");
        }
        catch (final IOException e)
        {
            throw new TerminalBillingException("Malformed Recurly response: " + e.getMessage());
        }
    }

    protected String writeJson(final JsonNode request)
    {
        try
        {
            return objectMapper.writeValueAsString(request);
        }
        catch (final JsonProcessingException exception)
        {
            throw new IllegalArgumentException("Could not serialize Recurly request", exception);
        }
    }

    protected void addExpiry(final ObjectNode request, final String expiry)
    {
        if (StringUtils.isBlank(expiry))
        {
            return;
        }
        final String[] parts = expiry.split("/");
        if (parts.length == 2)
        {
            putIfNotBlank(request, "month", StringUtils.stripStart(parts[0].trim(), "0"));
            putIfNotBlank(request, "year", parts[1].trim());
        }
    }

    protected void addBillingAddress(final ObjectNode request, final BillingAddress billingAddress)
    {
        if (billingAddress == null)
        {
            return;
        }

        putIfNotBlank(request, "first_name", billingAddress.firstName());
        putIfNotBlank(request, "last_name", billingAddress.lastName());
        final ObjectNode address = request.putObject("address");
        putIfNotBlank(address, "street1", billingAddress.street1());
        putIfNotBlank(address, "street2", billingAddress.street2());
        putIfNotBlank(address, "city", billingAddress.city());
        putIfNotBlank(address, "region", billingAddress.region());
        putIfNotBlank(address, "postal_code", billingAddress.postalCode());
        putIfNotBlank(address, "country", billingAddress.country());
        putIfNotBlank(address, "phone", billingAddress.phone());
    }

    protected static void putIfNotBlank(final ObjectNode node, final String key, final String value)
    {
        if (StringUtils.isNotBlank(value))
        {
            node.put(key, value);
        }
    }

    protected static String accountCodeId(final String customerId)
    {
        return "code-" + customerId;
    }

    protected static String pathSegment(final String value)
    {
        return URLEncoder.encode(StringUtils.defaultString(value), StandardCharsets.UTF_8).replace("+", "%20");
    }

    public void setHttpClient(final RecurlyHttpClient httpClient)
    {
        this.httpClient = httpClient;
    }

    public void setConfigService(final RecurlyConfigService configService)
    {
        this.configService = configService;
    }
}
