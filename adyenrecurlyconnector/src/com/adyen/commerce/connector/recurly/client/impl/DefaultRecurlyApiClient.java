package com.adyen.commerce.connector.recurly.client.impl;

import static java.net.HttpURLConnection.HTTP_CLIENT_TIMEOUT;
import static java.net.HttpURLConnection.HTTP_CONFLICT;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;

import com.adyen.commerce.connector.dto.BillingAddress;
import com.adyen.commerce.connector.dto.CardMetadata;
import com.adyen.commerce.connector.exception.BillingException;
import com.adyen.commerce.connector.exception.PreconditionFailedException;
import com.adyen.commerce.connector.exception.RetryableBillingException;
import com.adyen.commerce.connector.exception.TerminalBillingException;
import com.adyen.commerce.connector.recurly.client.RecurlyApiClient;
import com.adyen.commerce.connector.recurly.client.RecurlySubscriptionParams;
import com.adyen.commerce.connector.recurly.config.RecurlyConfigService;
import com.adyen.commerce.connector.recurly.http.RecurlyHttpClient;
import com.adyen.commerce.connector.recurly.http.RecurlyHttpResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Default Recurly client. It intentionally keeps vendor JSON translation inside the adapter and
 * surfaces only normalized {@link BillingException} failures to the SPI layer.
 */
public class DefaultRecurlyApiClient implements RecurlyApiClient {
    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RecurlyHttpClient httpClient;
    private final RecurlyConfigService configService;

    public DefaultRecurlyApiClient(final RecurlyHttpClient httpClient, final RecurlyConfigService configService) {
        this.httpClient = httpClient;
        this.configService = configService;
    }

    @Override
    public String ensureCustomer(final String customerId, final String email, final String firstName,
                                 final String lastName) throws BillingException {
        final String accountId = accountCodeId(customerId);
        final RecurlyHttpResponse existing = httpClient.get(url("/accounts/" + pathSegment(accountId)), authHeader(),
                acceptHeader());
        if (existing.statusCode() == HTTP_OK) {
            synchronizeAccountProfile(accountId, existing.body(), email, firstName, lastName);
            return accountId;
        }
        if (existing.statusCode() != HTTP_NOT_FOUND) {
            throw toBillingException(existing, "retrieve account");
        }

        if (!configService.isWalletEnabled()) {
            return accountId;
        }

        final ObjectNode request = objectMapper.createObjectNode();
        putIfNotBlank(request, "code", customerId);
        putIfNotBlank(request, "email", email);
        putIfNotBlank(request, "first_name", firstName);
        putIfNotBlank(request, "last_name", lastName);
        final RecurlyHttpResponse response = httpClient.post(url("/accounts"), authHeader(), acceptHeader(),
                writeJson(request), accountId);
        requireSuccess(response, "create account");
        return accountId;
    }

    @Override
    public String importAdyenToken(final String accountId, final String shopperReference,
                                   final String storedPaymentMethodId, final CardMetadata card,
                                   final BillingAddress billingAddress) throws BillingException {
        if (!configService.isWalletEnabled()) {
            return importPrimaryAdyenToken(accountId, shopperReference, storedPaymentMethodId, card, billingAddress);
        }

        final String billingInfosPath = "/accounts/" + pathSegment(accountId) + "/billing_infos";
        final RecurlyHttpResponse existing = httpClient.get(url(billingInfosPath), authHeader(), acceptHeader());
        requireSuccess(existing, "list billing infos");

        final List<JsonNode> billingInfos = readBillingInfos(existing.body());
        for (final JsonNode billingInfo : billingInfos) {
            if (billingInfoMatches(billingInfo, shopperReference, storedPaymentMethodId)) {
                return readId(billingInfo, "billing info");
            }
        }

        final ObjectNode request = buildAdyenBillingInfo(shopperReference, storedPaymentMethodId, card, billingAddress);
        if (!billingInfos.isEmpty()) {
            request.put("primary_payment_method", false);
        }
        final RecurlyHttpResponse response = httpClient.post(
                url(billingInfosPath), authHeader(), acceptHeader(),
                writeJson(request), fingerprintedKey(accountId + "/adyen", storedPaymentMethodId));
        requireSuccess(response, "add Adyen billing info");
        return readId(response.body());
    }

    @Override
    public String createSubscription(final RecurlySubscriptionParams params) throws BillingException {
        final ObjectNode request = objectMapper.createObjectNode();

        final ObjectNode account = request.putObject("account");
        putIfNotBlank(account, "code", accountCode(params.accountId()));

        if (configService.isWalletEnabled()) {
            putIfNotBlank(request, "billing_info_id", params.billingInfoId());
        }
        putIfNotBlank(request, "plan_code", params.planCode());
        request.put("quantity", Math.max(1, params.quantity()));
        putIfNotBlank(request, "currency", params.currencyIsoCode());
        putIfNotBlank(request, "starts_at", params.startsAt());
        putIfNotBlank(request, "network_transaction_id", params.networkTransactionId());

        if (params.metadata() != null && !params.metadata().isEmpty()) {
            final ArrayNode customFields = request.putArray("custom_fields");
            for (final Map.Entry<String, String> entry : params.metadata().entrySet()) {
                if (StringUtils.isNotBlank(entry.getKey()) && StringUtils.isNotBlank(entry.getValue())) {
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

    protected String importPrimaryAdyenToken(final String accountId, final String shopperReference,
                                             final String storedPaymentMethodId, final CardMetadata card,
                                             final BillingAddress billingAddress) throws BillingException {
        final String accountPath = "/accounts/" + pathSegment(accountId);
        final RecurlyHttpResponse account = httpClient.get(url(accountPath), authHeader(), acceptHeader());
        if (account.statusCode() == HTTP_NOT_FOUND) {
            final ObjectNode request = objectMapper.createObjectNode();
            putIfNotBlank(request, "code", accountCode(accountId));
            // Only a confirmed billing address names the account. An address inferred from the delivery
            // address carries the recipient's name, and on a gift order that is not the account holder —
            // every future invoice would be issued to the wrong person.
            if (billingAddress != null && billingAddress.confirmed()) {
                putIfNotBlank(request, "first_name", billingAddress.firstName());
                putIfNotBlank(request, "last_name", billingAddress.lastName());
            }
            request.set("billing_info",
                    buildAdyenBillingInfo(shopperReference, storedPaymentMethodId, card, billingAddress));

            final RecurlyHttpResponse response = httpClient.post(url("/accounts"), authHeader(), acceptHeader(),
                    writeJson(request), fingerprintedKey(accountId + "/primary-adyen", storedPaymentMethodId));
            requireSuccess(response, "create account with primary Adyen billing info");
            return retrievePrimaryBillingInfoId(accountId);
        }
        if (account.statusCode() != HTTP_OK) {
            throw toBillingException(account, "retrieve account before importing primary Adyen token");
        }

        final String billingInfoPath = accountPath + "/billing_info";
        final RecurlyHttpResponse existing = httpClient.get(url(billingInfoPath), authHeader(), acceptHeader());
        if (existing.statusCode() == HTTP_OK) {
            if (billingInfoMatches(existing.body(), shopperReference, storedPaymentMethodId)) {
                return readId(existing.body());
            }
            throw new PreconditionFailedException("Recurly account '" + accountId
                    + "' already has a different primary billing info and Subscriber Wallet is disabled; "
                    + "refusing to replace the customer's payment method implicitly");
        }
        if (existing.statusCode() != HTTP_NOT_FOUND) {
            throw toBillingException(existing, "retrieve primary billing info");
        }

        final ObjectNode billingInfo = buildAdyenBillingInfo(
                shopperReference, storedPaymentMethodId, card, billingAddress);
        final RecurlyHttpResponse response = httpClient.put(url(billingInfoPath), authHeader(), acceptHeader(),
                writeJson(billingInfo), fingerprintedKey(accountId + "/primary-adyen", storedPaymentMethodId));
        requireSuccess(response, "set primary Adyen billing info");
        return readId(response.body());
    }

    @Override
    public void updateSubscription(final String subscriptionId, final String planCode, final Integer quantity,
                                   final String idempotencyKey) throws BillingException {
        if (StringUtils.isBlank(planCode) && quantity == null) {
            throw new PreconditionFailedException(
                    "updateSubscription called with nothing to change for subscription '" + subscriptionId + "'");
        }

        final ObjectNode request = objectMapper.createObjectNode();
        putIfNotBlank(request, "plan_code", planCode);
        if (quantity != null) {
            request.put("quantity", quantity.intValue());
        }

        final RecurlyHttpResponse response = httpClient.post(
                url("/subscriptions/" + pathSegment(subscriptionId) + "/change"), authHeader(), acceptHeader(),
                writeJson(request), idempotencyKey);
        requireSuccess(response, "update subscription");
    }

    @Override
    public void cancelSubscription(final String subscriptionId, final boolean atPeriodEnd, final String idempotencyKey)
            throws BillingException {
        final String subscriptionPath = "/subscriptions/" + pathSegment(subscriptionId);
        if (atPeriodEnd) {
            final ObjectNode request = objectMapper.createObjectNode();
            request.put("timeframe", "bill_date");
            final RecurlyHttpResponse response = httpClient.put(url(subscriptionPath + "/cancel"), authHeader(),
                    acceptHeader(), writeJson(request), idempotencyKey);
            requireSuccess(response, "cancel subscription at next bill date");
        } else {
            final RecurlyHttpResponse response = httpClient.delete(url(subscriptionPath), authHeader(), acceptHeader(),
                    idempotencyKey);
            requireSuccess(response, "terminate subscription");
        }
    }

    @Override
    public List<String> resolveWebhookSubscriptionIds(final String resourceType, final String resourceId)
            throws BillingException {
        if (StringUtils.isAnyBlank(resourceType, resourceId)) {
            return List.of();
        }

        final String path;
        if ("payment".equals(resourceType)) {
            path = "/transactions/" + pathSegment(resourceId);
        } else if ("invoice".equals(resourceType) || "charge_invoice".equals(resourceType)) {
            path = "/invoices/" + pathSegment(resourceId);
        } else {
            return List.of();
        }

        final RecurlyHttpResponse response = httpClient.get(url(path), authHeader(), acceptHeader());
        requireSuccess(response, "resolve webhook " + resourceType);
        final JsonNode resource = readJson(response.body(), "webhook " + resourceType);
        final Set<String> subscriptionIds = new LinkedHashSet<>();
        collectSubscriptionIds(resource, subscriptionIds);
        collectSubscriptionIds(resource.path("invoice"), subscriptionIds);

        if (subscriptionIds.isEmpty() && "payment".equals(resourceType)) {
            final String invoiceId = resource.path("invoice").path("id").asText(null);
            if (StringUtils.isNotBlank(invoiceId)) {
                return resolveWebhookSubscriptionIds("invoice", invoiceId);
            }
        }
        return new ArrayList<>(subscriptionIds);
    }

    protected String authHeader() throws BillingException {
        final String encoded = Base64.getEncoder()
                .encodeToString((configService.getApiKey() + ":").getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    protected String acceptHeader() {
        return "application/vnd.recurly." + configService.getApiVersion() + "+json";
    }

    protected String url(final String path) throws BillingException {
        return configService.getApiBaseUrl() + (path.startsWith("/") ? path : "/" + path);
    }

    protected void requireSuccess(final RecurlyHttpResponse response, final String action) throws BillingException {
        if (!response.isSuccess()) {
            throw toBillingException(response, action);
        }
    }

    protected BillingException toBillingException(final RecurlyHttpResponse response, final String action) {
        final String detail = extractError(response.body());
        final String message = "Recurly " + action + " failed (HTTP " + response.statusCode() + ")"
                + (detail == null ? "" : ": " + detail);
        if (response.statusCode() == HTTP_CLIENT_TIMEOUT || response.statusCode() == HTTP_CONFLICT
                || response.statusCode() == HTTP_TOO_MANY_REQUESTS || response.statusCode() >= HTTP_INTERNAL_ERROR
                || StringUtils.containsIgnoreCase(detail, "simultaneous_request")) {
            return new RetryableBillingException(message);
        }
        return new TerminalBillingException(message);
    }

    protected String extractError(final String body) {
        if (StringUtils.isBlank(body)) {
            return null;
        }
        try {
            final JsonNode node = objectMapper.readTree(body);
            final String message = node.path("message").asText(node.path("error").path("message").asText(null));
            final String type = node.path("type").asText(node.path("error").path("type").asText(null));
            if (message == null && type == null) {
                return null;
            }
            return (type == null ? "" : "[" + type + "] ") + StringUtils.defaultString(message);
        } catch (final IOException e) {
            return null;
        }
    }

    protected String readId(final String body) throws BillingException {
        return readId(readJson(body, "response"), "response");
    }

    protected String readId(final JsonNode resource, final String resourceName) throws BillingException {
        final JsonNode id = resource.path("id");
        if (id.isMissingNode() || StringUtils.isBlank(id.asText(null))) {
            throw new TerminalBillingException("Recurly " + resourceName + " missing id");
        }
        return id.asText();
    }

    /**
     * JSON subscription webhooks identify subscriptions by UUID. Persist the API-compatible
     * {@code uuid-...} identifier so outbound lifecycle calls and inbound reconciliation use the same key.
     */
    protected String readSubscriptionId(final String body) throws BillingException {
        try {
            final JsonNode response = objectMapper.readTree(body);
            final String uuid = response.path("uuid").asText(null);
            if (StringUtils.isNotBlank(uuid)) {
                return "uuid-" + uuid;
            }
            final String id = response.path("id").asText(null);
            if (StringUtils.isNotBlank(id)) {
                return id;
            }
            throw new TerminalBillingException("Recurly subscription response missing id and uuid");
        } catch (final IOException e) {
            throw new TerminalBillingException("Malformed Recurly response: " + e.getMessage());
        }
    }

    protected String writeJson(final JsonNode request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (final JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not serialize Recurly request", exception);
        }
    }

    protected void addExpiry(final ObjectNode request, final String expiry) {
        if (StringUtils.isBlank(expiry)) {
            return;
        }
        final String[] parts = expiry.split("/");
        if (parts.length == 2) {
            putIfNotBlank(request, "month", StringUtils.stripStart(parts[0].trim(), "0"));
            putIfNotBlank(request, "year", parts[1].trim());
        }
    }

    protected void addBillingAddress(final ObjectNode request, final BillingAddress billingAddress) {
        if (billingAddress == null) {
            return;
        }

        // Same rule as the account: an inferred address still carries a usable address, but its name is
        // the recipient's, not the cardholder's, so it must not be sent as the billing name.
        if (billingAddress.confirmed()) {
            putIfNotBlank(request, "first_name", billingAddress.firstName());
            putIfNotBlank(request, "last_name", billingAddress.lastName());
        }
        final ObjectNode address = request.putObject("address");
        putIfNotBlank(address, "street1", billingAddress.street1());
        putIfNotBlank(address, "street2", billingAddress.street2());
        putIfNotBlank(address, "city", billingAddress.city());
        putIfNotBlank(address, "region", billingAddress.region());
        putIfNotBlank(address, "postal_code", billingAddress.postalCode());
        putIfNotBlank(address, "country", billingAddress.country());
        putIfNotBlank(address, "phone", billingAddress.phone());
    }

    protected ObjectNode buildAdyenBillingInfo(final String shopperReference, final String storedPaymentMethodId,
                                               final CardMetadata card, final BillingAddress billingAddress)
            throws BillingException {
        final ObjectNode billingInfo = objectMapper.createObjectNode();
        putIfNotBlank(billingInfo, "gateway_code", configService.getGatewayCode());

        final ObjectNode gatewayAttributes = billingInfo.putObject("gateway_attributes");
        putIfNotBlank(gatewayAttributes, "account_reference", shopperReference);

        final ArrayNode references = billingInfo.putArray("payment_gateway_references");
        final ObjectNode reference = references.addObject();
        putIfNotBlank(reference, "token", storedPaymentMethodId);

        if (card != null) {
            // Recurly derives display metadata such as last four from the imported gateway token. The API rejects
            // last_four in this request shape, so only accepted non-sensitive expiry metadata is forwarded.
            addExpiry(billingInfo, card.expiry());
        }
        addBillingAddress(billingInfo, billingAddress);
        return billingInfo;
    }

    protected void synchronizeAccountProfile(final String accountId, final String responseBody, final String email,
                                             final String firstName, final String lastName) throws BillingException {
        final JsonNode account = readJson(responseBody, "account");
        final ObjectNode update = objectMapper.createObjectNode();
        putIfDifferent(update, account, "email", email);
        putIfDifferent(update, account, "first_name", firstName);
        putIfDifferent(update, account, "last_name", lastName);
        if (update.isEmpty()) {
            return;
        }
        final String body = writeJson(update);
        final RecurlyHttpResponse response = httpClient.put(url("/accounts/" + pathSegment(accountId)), authHeader(),
                acceptHeader(), body, fingerprintedKey(accountId + "/profile", body));
        requireSuccess(response, "synchronize account");
    }

    protected boolean billingInfoMatches(final String responseBody, final String shopperReference,
                                         final String storedPaymentMethodId) throws BillingException {
        final JsonNode billingInfo = readJson(responseBody, "billing info");
        return billingInfoMatches(billingInfo, shopperReference, storedPaymentMethodId);
    }

    protected boolean billingInfoMatches(final JsonNode billingInfo, final String shopperReference,
                                         final String storedPaymentMethodId) {
        if (!StringUtils.equals(shopperReference,
                billingInfo.path("gateway_attributes").path("account_reference").asText(null))) {
            return false;
        }
        for (final JsonNode reference : billingInfo.path("payment_gateway_references")) {
            if (StringUtils.equals(storedPaymentMethodId, reference.path("token").asText(null))) {
                return true;
            }
        }
        return false;
    }

    protected List<JsonNode> readBillingInfos(final String body) throws BillingException {
        final JsonNode response = readJson(body, "billing infos");
        final JsonNode billingInfos = response.isArray() ? response : response.path("data");
        if (!billingInfos.isArray()) {
            throw new TerminalBillingException("Malformed Recurly billing infos response: expected an array");
        }

        final List<JsonNode> result = new ArrayList<>();
        billingInfos.forEach(result::add);
        return result;
    }

    protected String retrievePrimaryBillingInfoId(final String accountId) throws BillingException {
        final RecurlyHttpResponse response = httpClient.get(
                url("/accounts/" + pathSegment(accountId) + "/billing_info"), authHeader(), acceptHeader());
        if (response.statusCode() == HTTP_NOT_FOUND) {
            throw new TerminalBillingException("Recurly account '" + accountId
                    + "' has no primary billing info after importing the Adyen token");
        }
        requireSuccess(response, "retrieve primary billing info");
        return readId(response.body());
    }

    protected JsonNode readJson(final String body, final String resource) throws TerminalBillingException {
        try {
            return objectMapper.readTree(body);
        } catch (final IOException e) {
            throw new TerminalBillingException("Malformed Recurly " + resource + " response: " + e.getMessage());
        }
    }

    protected static void collectSubscriptionIds(final JsonNode resource, final Set<String> values) {
        if (resource == null || resource.isMissingNode()) {
            return;
        }
        final JsonNode ids = resource.path("subscription_ids");
        if (ids.isArray()) {
            ids.forEach(id -> addSubscriptionId(values, id.asText(null)));
        }
        addSubscriptionId(values, resource.path("subscription_id").asText(null));
    }

    protected static void addSubscriptionId(final Set<String> values, final String value) {
        if (StringUtils.isNotBlank(value)) {
            values.add(StringUtils.startsWith(value, "uuid-") ? value : "uuid-" + value);
        }
    }

    protected static void putIfDifferent(final ObjectNode update, final JsonNode existing, final String field,
                                         final String requested) {
        if (StringUtils.isNotBlank(requested) && !StringUtils.equals(requested, existing.path(field).asText(null))) {
            update.put(field, requested);
        }
    }

    protected static void putIfNotBlank(final ObjectNode node, final String key, final String value) {
        if (StringUtils.isNotBlank(value)) {
            node.put(key, value);
        }
    }

    protected static String accountCodeId(final String customerId) {
        return "code-" + customerId;
    }

    protected static String accountCode(final String accountId) {
        return StringUtils.removeStart(accountId, "code-");
    }

    protected static String pathSegment(final String value) {
        return URLEncoder.encode(StringUtils.defaultString(value), StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * Recurly idempotency keys can be retained in operational logs, so never embed an Adyen token or customer data.
     */
    protected static String fingerprintedKey(final String prefix, final String sensitiveValue) {
        return prefix + "/" + UUID.nameUUIDFromBytes(
                StringUtils.defaultString(sensitiveValue).getBytes(StandardCharsets.UTF_8));
    }

}
