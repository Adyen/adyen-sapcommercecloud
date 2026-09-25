package com.adyen.commerce.connector.recurly.client.impl;

import static java.net.HttpURLConnection.HTTP_CLIENT_TIMEOUT;
import static java.net.HttpURLConnection.HTTP_CONFLICT;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adyen.commerce.connector.dto.BillingAddress;
import com.adyen.commerce.connector.dto.BillingSubscriptionRef;
import com.adyen.commerce.connector.dto.CardMetadata;
import com.adyen.commerce.connector.dto.PlatformPaymentMethod;
import com.adyen.commerce.connector.dto.NormalizedSubscription;
import com.adyen.commerce.connector.dto.NormalizedSubscriptionStatus;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.BillingException;
import com.adyen.commerce.connector.exception.PreconditionFailedException;
import com.adyen.commerce.connector.exception.RetryableBillingException;
import com.adyen.commerce.connector.exception.TerminalBillingException;
import com.adyen.commerce.connector.log.ConnectorLogEvent;
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

/** Recurly v3 REST client. Vendor JSON stays inside; failures surface as {@link BillingException}. */
public class DefaultRecurlyApiClient implements RecurlyApiClient {
    /** Stopped renewing but still serving until {@code current_period_ends_at}. */
    protected static final String STATE_CANCELED = "canceled";

    private static final String BULLETS = "\u2022\u2022\u2022\u2022";

    /** Shown when Recurly reports no card detail, as for an externally vaulted token. */
    private static final String GENERIC_METHOD_LABEL = "Saved payment method";

    private static final Logger LOG = LoggerFactory.getLogger(DefaultRecurlyApiClient.class);

    /** Bare host names only, so the configured value cannot point the shopper's link at another origin. */
    private static final Pattern HOSTED_PAGES_HOST =
            Pattern.compile("[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?)+");
    private static final String EVENT_VENDOR_API_ERROR = "vendor_api_error";
    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    private static final String ACCOUNTS_PATH = "/accounts/";
    private static final String SUBSCRIPTIONS_PATH = "/subscriptions/";
    private static final String FIELD_ACCOUNT = "account";
    private static final String FIELD_FIRST_NAME = "first_name";
    private static final String FIELD_LAST_NAME = "last_name";
    private static final String FIELD_QUANTITY = "quantity";
    private static final String RESOURCE_INVOICE = "invoice";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RecurlyHttpClient httpClient;
    private RecurlyConfigService configService;

    @Override
    public String ensureCustomer(final String customerId, final String email, final String firstName,
                                 final String lastName) throws BillingException {
        final String accountId = accountCodeId(customerId);
        final RecurlyHttpResponse existing = httpClient.get(url(ACCOUNTS_PATH + pathSegment(accountId)), authHeader(),
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
        putIfNotBlank(request, FIELD_FIRST_NAME, firstName);
        putIfNotBlank(request, FIELD_LAST_NAME, lastName);
        final RecurlyHttpResponse response = httpClient.post(url("/accounts"), authHeader(), acceptHeader(),
                writeJson(request), accountId);
        requireSuccess(response, "create account");
        return accountId;
    }

    @Override
    public String importAdyenToken(final String accountId, final String shopperReference,
                                   final String storedPaymentMethodId, final CardMetadata card,
                                   final String networkTransactionId,
                                   final BillingAddress billingAddress) throws BillingException {
        if (!configService.isWalletEnabled()) {
            return importPrimaryAdyenToken(accountId, shopperReference, storedPaymentMethodId, card,
                    networkTransactionId, billingAddress);
        }

        final String billingInfosPath = ACCOUNTS_PATH + pathSegment(accountId) + "/billing_infos";
        final RecurlyHttpResponse existing = httpClient.get(url(billingInfosPath), authHeader(), acceptHeader());
        requireSuccess(existing, "list billing infos");

        final List<JsonNode> billingInfos = readBillingInfos(existing.body());
        for (final JsonNode billingInfo : billingInfos) {
            if (billingInfoMatches(billingInfo, shopperReference, storedPaymentMethodId)) {
                return readId(billingInfo, "billing info");
            }
        }

        final ObjectNode request = buildAdyenBillingInfo(shopperReference, storedPaymentMethodId, card,
                networkTransactionId, billingAddress);
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

        final ObjectNode account = request.putObject(FIELD_ACCOUNT);
        putIfNotBlank(account, "code", accountCode(params.accountId()));

        if (configService.isWalletEnabled()) {
            putIfNotBlank(request, "billing_info_id", params.billingInfoId());
        }
        putIfNotBlank(request, "plan_code", params.planCode());
        request.put(FIELD_QUANTITY, Math.max(1, params.quantity()));
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
                                             final String networkTransactionId,
                                             final BillingAddress billingAddress) throws BillingException {
        final String accountPath = ACCOUNTS_PATH + pathSegment(accountId);
        final RecurlyHttpResponse account = httpClient.get(url(accountPath), authHeader(), acceptHeader());
        if (account.statusCode() == HTTP_NOT_FOUND) {
            final ObjectNode request = objectMapper.createObjectNode();
            putIfNotBlank(request, "code", accountCode(accountId));
            // Only a confirmed billing address names the account: an inferred one carries the recipient's name.
            if (billingAddress != null && billingAddress.confirmed()) {
                putIfNotBlank(request, FIELD_FIRST_NAME, billingAddress.firstName());
                putIfNotBlank(request, FIELD_LAST_NAME, billingAddress.lastName());
            }
            request.set("billing_info",
                    buildAdyenBillingInfo(shopperReference, storedPaymentMethodId, card, networkTransactionId,
                            billingAddress));

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
                shopperReference, storedPaymentMethodId, card, networkTransactionId, billingAddress);
        final RecurlyHttpResponse response = httpClient.put(url(billingInfoPath), authHeader(), acceptHeader(),
                writeJson(billingInfo), fingerprintedKey(accountId + "/primary-adyen", storedPaymentMethodId));
        requireSuccess(response, "set primary Adyen billing info");
        return readId(response.body());
    }

    @Override
    public List<PlatformPaymentMethod> listBillingInfos(final String accountId) throws BillingException {
        final RecurlyHttpResponse response = httpClient.get(
                url(ACCOUNTS_PATH + pathSegment(accountId) + "/billing_infos"), authHeader(), acceptHeader());
        requireSuccess(response, "list billing infos");

        final List<PlatformPaymentMethod> methods = new ArrayList<>();
        for (final JsonNode billingInfo : readBillingInfos(response.body())) {
            final String id = billingInfo.path("id").asText(null);
            if (StringUtils.isBlank(id)) {
                continue;
            }
            methods.add(new PlatformPaymentMethod(id, describeBillingInfo(billingInfo),
                    cardMetadataOf(billingInfo), billingInfo.path("primary_payment_method").asBoolean(false),
                    importedTokenOf(billingInfo)));
        }
        return methods;
    }

    @Override
    public void promoteBillingInfoToPrimary(final String accountId, final String billingInfoId,
                                            final String idempotencyKey) throws BillingException {
        if (StringUtils.isBlank(billingInfoId)) {
            throw new PreconditionFailedException("promoteBillingInfoToPrimary called without a billing info "
                    + "for account '" + accountId + "'");
        }

        final ObjectNode request = objectMapper.createObjectNode();
        request.put("primary_payment_method", true);

        // Plural billing_infos path: the singular /billing_info rejects this field.
        final RecurlyHttpResponse response = httpClient.put(
                url(ACCOUNTS_PATH + pathSegment(accountId) + "/billing_infos/" + pathSegment(billingInfoId)),
                authHeader(), acceptHeader(), writeJson(request), idempotencyKey);
        requireSuccess(response, "promote billing info to primary");
    }

    @Override
    public String hostedAccountManagementUrl(final String accountId) throws BillingException {
        final String host = configService.getHostedPagesHost();
        if (!HOSTED_PAGES_HOST.matcher(StringUtils.defaultString(host)).matches()) {
            LOG.warn("Recurly hosted pages are enabled but hostedPagesHost is not a bare host name; "
                    + "not offering the shopper a link.");
            return null;
        }

        final RecurlyHttpResponse response = httpClient.get(
                url(ACCOUNTS_PATH + pathSegment(accountId)), authHeader(), acceptHeader());
        requireSuccess(response, "retrieve account");

        // A credential: it signs the holder into the account. Never logged.
        final String hostedLoginToken =
                readJson(response.body(), FIELD_ACCOUNT).path("hosted_login_token").asText(null);
        if (StringUtils.isBlank(hostedLoginToken)) {
            return null;
        }
        return "https://" + host + "/account/" + pathSegment(hostedLoginToken);
    }

    /** The gateway token this billing info was imported from, if any; lets the page match it to a vaulted card. */
    protected String importedTokenOf(final JsonNode billingInfo) {
        for (final JsonNode reference : billingInfo.path("payment_gateway_references")) {
            final String token = reference.path("token").asText(null);
            if (StringUtils.isNotBlank(token)) {
                return token;
            }
        }
        return null;
    }

    /** Shopper-facing label of a billing info. */
    protected String describeBillingInfo(final JsonNode billingInfo) {
        final JsonNode paymentMethod = billingInfo.path("payment_method");
        final String cardType = paymentMethod.path("card_type").asText(null);
        final String lastFour = paymentMethod.path("last_four").asText(null);
        if (StringUtils.isNotBlank(lastFour)) {
            return StringUtils.isBlank(cardType) ? BULLETS + " " + lastFour
                    : cardType + " " + BULLETS + " " + lastFour;
        }
        // Not payment_method.object: for a vaulted token that is the API enum "gateway_token".
        return GENERIC_METHOD_LABEL;
    }

    /** Display detail only; absent fields simply mean the page shows less. */
    protected CardMetadata cardMetadataOf(final JsonNode billingInfo) {
        final JsonNode paymentMethod = billingInfo.path("payment_method");
        final String lastFour = paymentMethod.path("last_four").asText(null);
        if (StringUtils.isBlank(lastFour)) {
            return null;
        }
        final String month = paymentMethod.path("exp_month").asText(null);
        final String year = paymentMethod.path("exp_year").asText(null);
        final String expiry = StringUtils.isAnyBlank(month, year) ? null
                : StringUtils.leftPad(month, 2, '0') + "/" + year;
        return new CardMetadata(paymentMethod.path("card_type").asText(null), lastFour, null, expiry, null);
    }

    @Override
    public void assignBillingInfo(final String subscriptionId, final String billingInfoId,
                                  final String idempotencyKey) throws BillingException {
        if (StringUtils.isBlank(billingInfoId)) {
            throw new PreconditionFailedException(
                    "assignBillingInfo called without a billing info for subscription '" + subscriptionId + "'");
        }

        final ObjectNode request = objectMapper.createObjectNode();
        request.put("billing_info_id", billingInfoId);

        // PUT, not /change: /change alters what is billed and raises an invoice.
        final RecurlyHttpResponse response = httpClient.put(
                url(SUBSCRIPTIONS_PATH + pathSegment(subscriptionId)), authHeader(), acceptHeader(),
                writeJson(request), idempotencyKey);
        requireSuccess(response, "assign billing info to subscription");
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
            request.put(FIELD_QUANTITY, quantity.intValue());
        }

        final RecurlyHttpResponse response = httpClient.post(
                url(SUBSCRIPTIONS_PATH + pathSegment(subscriptionId) + "/change"), authHeader(), acceptHeader(),
                writeJson(request), idempotencyKey);
        requireSuccess(response, "update subscription");
    }

    @Override
    public void cancelAtNextBillDate(final String subscriptionId, final String idempotencyKey)
            throws BillingException {
        final ObjectNode request = objectMapper.createObjectNode();
        request.put("timeframe", "bill_date");
        final RecurlyHttpResponse response = httpClient.put(
                url(SUBSCRIPTIONS_PATH + pathSegment(subscriptionId) + "/cancel"), authHeader(), acceptHeader(),
                writeJson(request), idempotencyKey);
        requireSuccess(response, "cancel subscription at next bill date");
    }

    @Override
    public void terminate(final String subscriptionId, final String idempotencyKey) throws BillingException {
        final RecurlyHttpResponse response = httpClient.delete(url(SUBSCRIPTIONS_PATH + pathSegment(subscriptionId)),
                authHeader(), acceptHeader(), idempotencyKey);
        requireSuccess(response, "terminate subscription");
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
        } else if (RESOURCE_INVOICE.equals(resourceType) || "charge_invoice".equals(resourceType)) {
            path = "/invoices/" + pathSegment(resourceId);
        } else {
            return List.of();
        }

        final RecurlyHttpResponse response = httpClient.get(url(path), authHeader(), acceptHeader());
        requireSuccess(response, "resolve webhook " + resourceType);
        final JsonNode resource = readJson(response.body(), "webhook " + resourceType);
        final Set<String> subscriptionIds = new LinkedHashSet<>();
        collectSubscriptionIds(resource, subscriptionIds);
        collectSubscriptionIds(resource.path(RESOURCE_INVOICE), subscriptionIds);

        if (subscriptionIds.isEmpty() && "payment".equals(resourceType)) {
            final String invoiceId = resource.path(RESOURCE_INVOICE).path("id").asText(null);
            if (StringUtils.isNotBlank(invoiceId)) {
                return resolveWebhookSubscriptionIds(RESOURCE_INVOICE, invoiceId);
            }
        }
        return new ArrayList<>(subscriptionIds);
    }

    @Override
    public NormalizedSubscription fetchSubscription(final String subscriptionId)
            throws BillingException
    {
        final RecurlyHttpResponse response = httpClient.get(
                url(SUBSCRIPTIONS_PATH + pathSegment(subscriptionId)),
                authHeader(),
                acceptHeader());

        requireSuccess(response, "retrieve subscription");
        return mapSubscription(response.body(), subscriptionId);
    }

    protected NormalizedSubscription mapSubscription(final String body, final String requestedSubscriptionId)
            throws BillingException {
        final JsonNode subscription = readJson(body, "subscription");
        final String subscriptionId = readSubscriptionId(subscription);
        final NormalizedSubscriptionStatus lifecycleStatus = mapStatus(subscription.path("state").asText(null));
        final NormalizedSubscriptionStatus status = isPastDueEligible(lifecycleStatus)
                && hasPastDueInvoice(subscription, requestedSubscriptionId, subscriptionId)
                ? NormalizedSubscriptionStatus.PAST_DUE
                : lifecycleStatus;

        return new NormalizedSubscription(
                new BillingSubscriptionRef(BillingPlatform.RECURLY, subscriptionId),
                status,
                subscription.path("plan").path("code").asText(null),
                subscription.path(FIELD_QUANTITY).asInt(1),
                parseInstant(subscription.path("current_period_started_at")),
                parseInstant(subscription.path("current_period_ends_at")),
                isCancelAtPeriodEnd(subscription),
                parseInstant(subscription.path("updated_at")));
    }

    /** A {@code canceled} state wins over a missing {@code auto_renew}, which would otherwise read as renewing. */
    protected boolean isCancelAtPeriodEnd(final JsonNode subscription) {
        return STATE_CANCELED.equalsIgnoreCase(subscription.path("state").asText(null))
                || !subscription.path("auto_renew").asBoolean(true);
    }

    protected boolean hasPastDueInvoice(final JsonNode subscription, final String requestedSubscriptionId,
                                        final String normalizedSubscriptionId) throws BillingException {
        final String accountId = subscription.path(FIELD_ACCOUNT).path("id").asText(null);
        final String accountCode = subscription.path(FIELD_ACCOUNT).path("code").asText(null);
        final String accountReference = StringUtils.isNotBlank(accountId)
                ? accountId
                : StringUtils.isNotBlank(accountCode) ? "code-" + accountCode : null;
        if (StringUtils.isBlank(accountReference)) {
            throw new TerminalBillingException("Recurly subscription response missing account id and code");
        }

        // A cursor pointing back at a page already read would otherwise loop forever.
        final Set<String> visitedPages = new LinkedHashSet<>();
        String nextUrl = url(ACCOUNTS_PATH + pathSegment(accountReference) + "/invoices?state=past_due&limit=200");
        while (StringUtils.isNotBlank(nextUrl)) {
            validateRecurlyPageUrl(nextUrl);
            if (!visitedPages.add(nextUrl)) {
                throw new TerminalBillingException("Recurly invoice pagination repeated a page");
            }
            final RecurlyHttpResponse response = httpClient.get(nextUrl, authHeader(), acceptHeader());
            requireSuccess(response, "list past-due account invoices");
            final JsonNode page = readJson(response.body(), "past-due invoices");
            for (final JsonNode invoice : page.path("data")) {
                final Set<String> invoiceSubscriptions = new LinkedHashSet<>();
                collectSubscriptionIds(invoice, invoiceSubscriptions);
                if (containsSubscription(invoiceSubscriptions, requestedSubscriptionId, normalizedSubscriptionId)) {
                    return true;
                }
            }
            nextUrl = page.path("has_more").asBoolean(false)
                    ? resolvePageUrl(page.path("next").asText(null))
                    : null;
        }
        return false;
    }

    /** Joins Recurly's site-relative {@code next} to the base; {@link #validateRecurlyPageUrl} still checks it. */
    protected String resolvePageUrl(final String next) throws BillingException {
        // "//host/path" names its own host; left as is, the validation rejects it.
        if (StringUtils.startsWith(next, "/") && !StringUtils.startsWith(next, "//")) {
            return url(next);
        }
        return next;
    }

    protected void validateRecurlyPageUrl(final String pageUrl) throws BillingException {
        final String baseUrl = configService.getApiBaseUrl();
        if (!StringUtils.startsWith(pageUrl, baseUrl + "/")) {
            throw new TerminalBillingException("Recurly invoice pagination returned an unexpected URL");
        }
    }

    protected boolean containsSubscription(final Set<String> invoiceSubscriptions, final String requestedId,
                                           final String normalizedId) {
        final Set<String> expected = new LinkedHashSet<>();
        addSubscriptionId(expected, requestedId);
        addSubscriptionId(expected, normalizedId);
        return invoiceSubscriptions.stream().anyMatch(expected::contains);
    }

    protected boolean isPastDueEligible(final NormalizedSubscriptionStatus status) {
        return status == NormalizedSubscriptionStatus.ACTIVE || status == NormalizedSubscriptionStatus.PAUSED;
    }

    /**
     * {@code canceled} still serves until the period ends, so it is ACTIVE with {@code cancelAtPeriodEnd};
     * {@code expired} is EXPIRED. Same mapping as Chargebee's {@code non_renewing} and {@code cancelled}.
     */
    protected NormalizedSubscriptionStatus mapStatus(final String recurlyState) {
        if (StringUtils.isBlank(recurlyState)) {
            return NormalizedSubscriptionStatus.UNKNOWN;
        }
        return switch (recurlyState.toLowerCase(java.util.Locale.ROOT)) {
            case "active", STATE_CANCELED -> NormalizedSubscriptionStatus.ACTIVE;
            case "future" -> NormalizedSubscriptionStatus.PENDING;
            case "paused" -> NormalizedSubscriptionStatus.PAUSED;
            case "expired" -> NormalizedSubscriptionStatus.EXPIRED;
            case "failed" -> NormalizedSubscriptionStatus.FAILED;
            default -> NormalizedSubscriptionStatus.UNKNOWN;
        };
    }

    protected Instant parseInstant(final JsonNode node) throws TerminalBillingException {
        if (node == null || node.isMissingNode() || node.isNull() || StringUtils.isBlank(node.asText(null))) {
            return null;
        }
        try {
            return Instant.parse(node.asText());
        } catch (final DateTimeParseException exception) {
            throw new TerminalBillingException("Malformed Recurly subscription timestamp '" + node.asText() + "'",
                    exception);
        }
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

    /** Never throws: it runs while a failure is already being handled. */
    protected BillingException toBillingException(final RecurlyHttpResponse response, final String action) {
        final String detail = extractError(response.body());
        final String message = "Recurly " + action + " failed (HTTP " + response.statusCode() + ")"
                + (detail == null ? "" : ": " + detail);
        final boolean retryable = response.statusCode() == HTTP_CLIENT_TIMEOUT || response.statusCode() == HTTP_CONFLICT
                || response.statusCode() == HTTP_TOO_MANY_REQUESTS || response.statusCode() >= HTTP_INTERNAL_ERROR
                || StringUtils.containsIgnoreCase(detail, "simultaneous_request");
        // Only the error code is logged: the vendor's message can echo submitted values.
        ConnectorLogEvent.of(EVENT_VENDOR_API_ERROR)
                .platform(BillingPlatform.RECURLY)
                .outcome(ConnectorLogEvent.OUTCOME_FAILURE)
                .field("vendor_action", action.replace(' ', '_'))
                .field("http_status", response.statusCode())
                .field("error_class", ConnectorLogEvent.httpErrorClass(response.statusCode()))
                .field("vendor_error_code", errorCode(response.body()))
                .field("retryable", retryable)
                .warn(LOG);
        if (retryable) {
            return new RetryableBillingException(message);
        }
        return new TerminalBillingException(message);
    }

    /** {@code [type] message}, for the exception text. */
    protected String extractError(final String body) {
        final JsonNode node = readErrorTree(body);
        if (node == null) {
            return null;
        }
        final String message = node.path("message").asText(node.path("error").path("message").asText(null));
        final String type = errorType(node);
        if (message == null && type == null) {
            return null;
        }
        return (type == null ? "" : "[" + type + "] ") + StringUtils.defaultString(message);
    }

    /** The machine-readable error type, for log labels. */
    protected String errorCode(final String body) {
        final JsonNode node = readErrorTree(body);
        return node == null ? null : errorType(node);
    }

    /** Recurly returns some errors at the top level and wraps others in {@code error}. */
    protected static String errorType(final JsonNode node) {
        return node.path("type").asText(node.path("error").path("type").asText(null));
    }

    protected JsonNode readErrorTree(final String body) {
        if (StringUtils.isBlank(body)) {
            return null;
        }
        try {
            return objectMapper.readTree(body);
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

    protected String readSubscriptionId(final String body) throws BillingException {
        try {
            return readSubscriptionId(objectMapper.readTree(body));
        } catch (final IOException e) {
            throw new TerminalBillingException("Malformed Recurly response", e);
        }
    }

    /** {@code uuid-<uuid>}, the id form both the API and the webhooks accept. */
    protected String readSubscriptionId(final JsonNode subscription) throws BillingException {
        final String uuid = subscription.path("uuid").asText(null);
        if (StringUtils.isNotBlank(uuid)) {
            return "uuid-" + uuid;
        }
        final String id = subscription.path("id").asText(null);
        if (StringUtils.isNotBlank(id)) {
            return id;
        }
        throw new TerminalBillingException("Recurly subscription response missing id and uuid");
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

        // An inferred address carries the recipient's name, not the cardholder's.
        if (billingAddress.confirmed()) {
            putIfNotBlank(request, FIELD_FIRST_NAME, billingAddress.firstName());
            putIfNotBlank(request, FIELD_LAST_NAME, billingAddress.lastName());
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
                                               final CardMetadata card, final String networkTransactionId,
                                               final BillingAddress billingAddress)
            throws BillingException {
        final ObjectNode billingInfo = objectMapper.createObjectNode();
        putIfNotBlank(billingInfo, "gateway_code", configService.getGatewayCode());

        final ObjectNode gatewayAttributes = billingInfo.putObject("gateway_attributes");
        putIfNotBlank(gatewayAttributes, "account_reference", shopperReference);

        final ArrayNode references = billingInfo.putArray("payment_gateway_references");
        final ObjectNode reference = references.addObject();
        putIfNotBlank(reference, "token", storedPaymentMethodId);

        // Recurly documents the NTID only on /subscriptions and /purchases, so this is behind its own switch.
        if (configService.isNetworkTransactionIdOnBillingInfoEnabled()) {
            putIfNotBlank(billingInfo, "network_transaction_id", networkTransactionId);
        }

        if (card != null) {
            // Recurly derives last four from the token and rejects it here; only the expiry is sent.
            addExpiry(billingInfo, card.expiry());
        }
        addBillingAddress(billingInfo, billingAddress);
        return billingInfo;
    }

    protected void synchronizeAccountProfile(final String accountId, final String responseBody, final String email,
                                             final String firstName, final String lastName) throws BillingException {
        final JsonNode account = readJson(responseBody, FIELD_ACCOUNT);
        final ObjectNode update = objectMapper.createObjectNode();
        putIfDifferent(update, account, "email", email);
        putIfDifferent(update, account, FIELD_FIRST_NAME, firstName);
        putIfDifferent(update, account, FIELD_LAST_NAME, lastName);
        if (update.isEmpty()) {
            return;
        }
        final String body = writeJson(update);
        final RecurlyHttpResponse response = httpClient.put(url(ACCOUNTS_PATH + pathSegment(accountId)), authHeader(),
                acceptHeader(), body, fingerprintedKey(accountId + "/profile", body));
        requireSuccess(response, "synchronize account");
    }

    protected boolean billingInfoMatches(final String responseBody, final String shopperReference,
                                         final String storedPaymentMethodId) throws BillingException {
        final JsonNode billingInfo = readJson(responseBody, "billing info");
        return billingInfoMatches(billingInfo, shopperReference, storedPaymentMethodId);
    }

    /**
     * Whether this billing info already represents the Adyen token. Deduplication only, not an ownership
     * check: the account is the ownership boundary.
     */
    protected boolean billingInfoMatches(final JsonNode billingInfo, final String shopperReference,
                                         final String storedPaymentMethodId) {
        if (!StringUtils.equals(shopperReference, accountReferenceOf(billingInfo))) {
            return false;
        }
        for (final JsonNode reference : billingInfo.path("payment_gateway_references")) {
            if (StringUtils.equals(storedPaymentMethodId, reference.path("token").asText(null))) {
                return true;
            }
        }
        return false;
    }

    /** The Adyen shopper reference: top level on create, under {@code payment_method} when read back. */
    protected String accountReferenceOf(final JsonNode billingInfo) {
        final String nested = billingInfo.path("payment_method").path("gateway_attributes")
                .path("account_reference").asText(null);
        return nested != null ? nested
                : billingInfo.path("gateway_attributes").path("account_reference").asText(null);
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
                url(ACCOUNTS_PATH + pathSegment(accountId) + "/billing_info"), authHeader(), acceptHeader());
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
            throw new TerminalBillingException("Malformed Recurly " + resource + " response", e);
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

    /** Recurly may log idempotency keys, so the sensitive part is hashed. */
    protected static String fingerprintedKey(final String prefix, final String sensitiveValue) {
        return prefix + "/" + UUID.nameUUIDFromBytes(
                StringUtils.defaultString(sensitiveValue).getBytes(StandardCharsets.UTF_8));
    }

    public void setHttpClient(final RecurlyHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public void setConfigService(final RecurlyConfigService configService) {
        this.configService = configService;
    }
}
