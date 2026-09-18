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

/**
 * Default Recurly client. It intentionally keeps vendor JSON translation inside the adapter and
 * surfaces only normalized {@link BillingException} failures to the SPI layer.
 */
public class DefaultRecurlyApiClient implements RecurlyApiClient {
    /**
     * Recurly's own name for a subscription that has stopped renewing but keeps serving the customer until
     * {@code current_period_ends_at}. Shared by the status mapping and the {@code cancelAtPeriodEnd}
     * derivation so the literal lives in one place.
     */
    protected static final String STATE_CANCELED = "canceled";

    private static final String BULLETS = "\u2022\u2022\u2022\u2022";

    /** Shown when Recurly reports no card detail, which is the case for an externally vaulted token. */
    private static final String GENERIC_METHOD_LABEL = "Saved payment method";

    private static final Logger LOG = LoggerFactory.getLogger(DefaultRecurlyApiClient.class);

    /** Bare host names only, so nothing configured can turn the shopper's link into a different origin. */
    private static final Pattern HOSTED_PAGES_HOST =
            Pattern.compile("[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?)+");
    private static final String EVENT_VENDOR_API_ERROR = "vendor_api_error";
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
                                   final String networkTransactionId,
                                   final BillingAddress billingAddress) throws BillingException {
        if (!configService.isWalletEnabled()) {
            return importPrimaryAdyenToken(accountId, shopperReference, storedPaymentMethodId, card,
                    networkTransactionId, billingAddress);
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
                                             final String networkTransactionId,
                                             final BillingAddress billingAddress) throws BillingException {
        final String accountPath = "/accounts/" + pathSegment(accountId);
        final RecurlyHttpResponse account = httpClient.get(url(accountPath), authHeader(), acceptHeader());
        if (account.statusCode() == HTTP_NOT_FOUND) {
            final ObjectNode request = objectMapper.createObjectNode();
            putIfNotBlank(request, "code", accountCode(accountId));
            // Only a confirmed billing address names the account. An address inferred from the delivery address
            // carries the recipient's name, and on a gift order that is not the account holder - every future
            // invoice would be issued to the wrong person.
            if (billingAddress != null && billingAddress.confirmed()) {
                putIfNotBlank(request, "first_name", billingAddress.firstName());
                putIfNotBlank(request, "last_name", billingAddress.lastName());
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
                url("/accounts/" + pathSegment(accountId) + "/billing_infos"), authHeader(), acceptHeader());
        requireSuccess(response, "list billing infos");

        final List<PlatformPaymentMethod> methods = new ArrayList<>();
        for (final JsonNode billingInfo : readBillingInfos(response.body())) {
            final String id = billingInfo.path("id").asText(null);
            if (StringUtils.isBlank(id)) {
                continue;
            }
            methods.add(new PlatformPaymentMethod(id, describeBillingInfo(billingInfo),
                    cardMetadataOf(billingInfo), billingInfo.path("primary_payment_method").asBoolean(false)));
        }
        return methods;
    }

    @Override
    public String hostedAccountManagementUrl(final String accountId) throws BillingException {
        final String host = configService.getHostedPagesHost();
        if (!HOSTED_PAGES_HOST.matcher(StringUtils.defaultString(host)).matches()) {
            // Misconfiguration, not a shopper-visible failure: a host carrying a scheme, a path or an
            // "@" would redirect somewhere other than Recurly once the token is appended to it.
            LOG.warn("Recurly hosted pages are enabled but hostedPagesHost is not a bare host name; "
                    + "not offering the shopper a link.");
            return null;
        }

        final RecurlyHttpResponse response = httpClient.get(
                url("/accounts/" + pathSegment(accountId)), authHeader(), acceptHeader());
        requireSuccess(response, "retrieve account");

        // A credential, not an identifier: it signs the holder into the account. It is returned to exactly
        // one caller, is never logged, and nothing else on this class reads it.
        final String hostedLoginToken =
                readJson(response.body(), "account").path("hosted_login_token").asText(null);
        if (StringUtils.isBlank(hostedLoginToken)) {
            return null;
        }
        return "https://" + host + "/account/" + pathSegment(hostedLoginToken);
    }

    /**
     * How a billing info is named on the shopper's page. Composed here because only this adapter knows that
     * a Recurly billing info may be a card, a PayPal agreement or a bank account; it falls through to the
     * identifier rather than to an empty label, so two rows are never indistinguishable.
     */
    protected String describeBillingInfo(final JsonNode billingInfo) {
        final JsonNode paymentMethod = billingInfo.path("payment_method");
        final String cardType = paymentMethod.path("card_type").asText(null);
        final String lastFour = paymentMethod.path("last_four").asText(null);
        if (StringUtils.isNotBlank(lastFour)) {
            return StringUtils.isBlank(cardType) ? BULLETS + " " + lastFour
                    : cardType + " " + BULLETS + " " + lastFour;
        }
        // Never payment_method.object: Recurly reports an externally vaulted method as "gateway_token",
        // which is an API enum and not something to put in front of a shopper. Nor the billing info id.
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

        // PUT, not the /change endpoint: /change alters what is billed and would raise an invoice, while this
        // alters only which instrument the next billing event uses. Recurly documents the field as Wallet-only
        // and rejects it elsewhere, which is why the capability is gated on Wallet.
        final RecurlyHttpResponse response = httpClient.put(
                url("/subscriptions/" + pathSegment(subscriptionId)), authHeader(), acceptHeader(),
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
            request.put("quantity", quantity.intValue());
        }

        final RecurlyHttpResponse response = httpClient.post(
                url("/subscriptions/" + pathSegment(subscriptionId) + "/change"), authHeader(), acceptHeader(),
                writeJson(request), idempotencyKey);
        requireSuccess(response, "update subscription");
    }

    @Override
    public void cancelAtNextBillDate(final String subscriptionId, final String idempotencyKey)
            throws BillingException {
        final ObjectNode request = objectMapper.createObjectNode();
        request.put("timeframe", "bill_date");
        final RecurlyHttpResponse response = httpClient.put(
                url("/subscriptions/" + pathSegment(subscriptionId) + "/cancel"), authHeader(), acceptHeader(),
                writeJson(request), idempotencyKey);
        requireSuccess(response, "cancel subscription at next bill date");
    }

    @Override
    public void terminate(final String subscriptionId, final String idempotencyKey) throws BillingException {
        final RecurlyHttpResponse response = httpClient.delete(url("/subscriptions/" + pathSegment(subscriptionId)),
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

    @Override
    public NormalizedSubscription fetchSubscription(final String subscriptionId)
            throws BillingException
    {
        final RecurlyHttpResponse response = httpClient.get(
                url("/subscriptions/" + pathSegment(subscriptionId)),
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
                subscription.path("quantity").asInt(1),
                parseInstant(subscription.path("current_period_started_at")),
                parseInstant(subscription.path("current_period_ends_at")),
                isCancelAtPeriodEnd(subscription),
                parseInstant(subscription.path("updated_at")));
    }

    /**
     * The pending end is read from the state as well as {@code auto_renew}: a {@code canceled} subscription
     * is by definition no longer renewing, and a missing {@code auto_renew} reads as "renewing", which would
     * contradict the state beside it and hide the pending end from callers that only look at
     * {@code cancelAtPeriodEnd}.
     */
    protected boolean isCancelAtPeriodEnd(final JsonNode subscription) {
        return STATE_CANCELED.equalsIgnoreCase(subscription.path("state").asText(null))
                || !subscription.path("auto_renew").asBoolean(true);
    }

    protected boolean hasPastDueInvoice(final JsonNode subscription, final String requestedSubscriptionId,
                                        final String normalizedSubscriptionId) throws BillingException {
        final String accountId = subscription.path("account").path("id").asText(null);
        final String accountCode = subscription.path("account").path("code").asText(null);
        final String accountReference = StringUtils.isNotBlank(accountId)
                ? accountId
                : StringUtils.isNotBlank(accountCode) ? "code-" + accountCode : null;
        if (StringUtils.isBlank(accountReference)) {
            throw new TerminalBillingException("Recurly subscription response missing account id and code");
        }

        // A page URL is only required to sit under the configured base, so a cursor pointing back at a page
        // already read would keep this walk calling Recurly forever. No legitimate pagination repeats a page.
        final Set<String> visitedPages = new LinkedHashSet<>();
        String nextUrl = url("/accounts/" + pathSegment(accountReference) + "/invoices?state=past_due&limit=200");
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

    /**
     * Recurly returns {@code next} as a site-relative path, so it is a usable URL only once joined to the
     * configured base. Resolution vouches for nothing: every page URL still goes through
     * {@link #validateRecurlyPageUrl(String)}, so a spoofed or corrupted response cannot aim the
     * credentialed request at a host other than the configured one.
     */
    protected String resolvePageUrl(final String next) throws BillingException {
        // A protocol-relative reference ("//host/path") names its own authority, so it is not a site-relative
        // path. Leaving it untouched lets the guard below reject it as the foreign host it is.
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
     * Recurly's {@code canceled} stops renewal but keeps serving the customer until
     * {@code current_period_ends_at} and can be reactivated, so it maps to ACTIVE and the pending end travels
     * as {@code cancelAtPeriodEnd} - the Chargebee adapter normalizes the same state, {@code non_renewing},
     * the same way. {@code expired} is the state Recurly moves a subscription into once its term has run out,
     * and maps to EXPIRED, as Chargebee's {@code cancelled} does. Neither adapter produces CANCELLED; see
     * {@code NormalizedSubscriptionStatus}.
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

    /**
     * Builds - never throws. It runs on a path that is already handling a failure, so an exception raised
     * while classifying one would replace the HTTP status and the vendor's own explanation, and lose the
     * retryable/terminal decision the core's retry policy is about to read.
     */
    protected BillingException toBillingException(final RecurlyHttpResponse response, final String action) {
        final String detail = extractError(response.body());
        final String message = "Recurly " + action + " failed (HTTP " + response.statusCode() + ")"
                + (detail == null ? "" : ": " + detail);
        final boolean retryable = response.statusCode() == HTTP_CLIENT_TIMEOUT || response.statusCode() == HTTP_CONFLICT
                || response.statusCode() == HTTP_TOO_MANY_REQUESTS || response.statusCode() >= HTTP_INTERNAL_ERROR
                || StringUtils.containsIgnoreCase(detail, "simultaneous_request");
        // The vendor's error code only. The prose that comes with it can echo submitted values back, so
        // it stays in the exception - which travels to the dead letter - and out of the log line.
        ConnectorLogEvent.of(EVENT_VENDOR_API_ERROR)
                .platform(BillingPlatform.RECURLY)
                .outcome(ConnectorLogEvent.OUTCOME_FAILURE)
                .field("vendor_action", action.replace(' ', '_'))
                .field("http_status", Integer.valueOf(response.statusCode()))
                .field("error_class", ConnectorLogEvent.httpErrorClass(response.statusCode()))
                .field("vendor_error_code", errorCode(response.body()))
                .field("retryable", Boolean.valueOf(retryable))
                .warn(LOG);
        if (retryable) {
            return new RetryableBillingException(message);
        }
        return new TerminalBillingException(message);
    }

    /**
     * The error as a human reads it: {@code [type] message}. The message is the only part that says
     * <em>which</em> field or value Recurly refused.
     */
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

    /**
     * Just the machine-readable error type, for log labels: bounded cardinality and no shopper data.
     */
    protected String errorCode(final String body) {
        final JsonNode node = readErrorTree(body);
        return node == null ? null : errorType(node);
    }

    /**
     * Recurly returns some errors at the top level and wraps others in {@code error}.
     */
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

    /**
     * For callers holding only the raw body. A caller that has already parsed the response must use
     * {@link #readSubscriptionId(JsonNode)} rather than pay for a second parse of the same payload.
     */
    protected String readSubscriptionId(final String body) throws BillingException {
        try {
            return readSubscriptionId(objectMapper.readTree(body));
        } catch (final IOException e) {
            throw new TerminalBillingException("Malformed Recurly response", e);
        }
    }

    /**
     * JSON subscription webhooks identify subscriptions by UUID. Persist the API-compatible
     * {@code uuid-...} identifier so outbound lifecycle calls and inbound reconciliation use the same key.
     */
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

        // Same rule as the account: an inferred address carries the recipient's name, not the cardholder's,
        // so it must not be sent as the billing name.
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

        // Behind its own switch, and unproven: Recurly documents the network transaction id as accepted on
        // /subscriptions and /purchases, and says nothing about billing infos. With the switch off the body
        // is byte-for-byte what it has always been, so the working import path cannot regress into an
        // experiment.
        if (configService.isNetworkTransactionIdOnBillingInfoEnabled()) {
            putIfNotBlank(billingInfo, "network_transaction_id", networkTransactionId);
        }

        if (card != null) {
            // Recurly derives display metadata such as last four from the imported gateway token, and rejects
            // last_four in this request shape, so only the accepted non-sensitive expiry metadata is forwarded.
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

    /**
     * Whether this billing info is the one already representing that Adyen token.
     *
     * <p>Deduplication, never authorisation. It answers whether this token has already been imported here,
     * which is what stops a repeat order creating a second billing info. It must not be reused to decide
     * whether a billing info belongs to a shopper: the account is already the shopper's, and filtering on
     * the Adyen account reference would exclude exactly the billing infos Recurly supports best - the ones
     * collected by its own hosted pages, by support, or by the Account Updater.</p>
     *
     * <p>Both nestings are accepted because the shape Recurly takes differs from the shape it returns:
     * {@code BillingInfoCreate} carries {@code gateway_attributes} at the top level, which is what
     * {@link #buildAdyenBillingInfo} sends, while the {@code BillingInfo} read back carries it under
     * {@code payment_method}. Only {@code payment_gateway_references} stays at the top in both.</p>
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

    /** The Adyen shopper reference Recurly holds for this billing info, from whichever shape it arrived in. */
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

    /**
     * Recurly idempotency keys can be retained in operational logs, so never embed an Adyen token or customer data.
     */
    protected static String fingerprintedKey(final String prefix, final String sensitiveValue) {
        return prefix + "/" + UUID.nameUUIDFromBytes(
                StringUtils.defaultString(sensitiveValue).getBytes(StandardCharsets.UTF_8));
    }
}
