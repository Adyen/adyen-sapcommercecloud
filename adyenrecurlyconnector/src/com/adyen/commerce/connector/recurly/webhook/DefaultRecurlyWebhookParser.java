package com.adyen.commerce.connector.recurly.webhook;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adyen.commerce.connector.dto.BillingEventType;
import com.adyen.commerce.connector.dto.NormalizedBillingEvent;
import com.adyen.commerce.connector.dto.RawWebhook;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.BillingException;
import com.adyen.commerce.connector.exception.TerminalBillingException;
import com.adyen.commerce.connector.log.ConnectorLogEvent;
import com.adyen.commerce.connector.recurly.config.RecurlyConfigService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Verifies and normalizes Recurly's signed JSON webhook format.
 */
public class DefaultRecurlyWebhookParser implements RecurlyWebhookParser {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultRecurlyWebhookParser.class);
    private static final String EVENT_WEBHOOK_PROCESSING = "webhook_processing";
    private static final String SIGNATURE_HEADER = "recurly-signature";
    private static final String NOTIFICATION_ID_HEADER = "recurly-notification-id";
    private static final String HMAC_SHA_256 = "HmacSHA256";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock clock;
    private final RecurlyConfigService configService;

    public DefaultRecurlyWebhookParser(final RecurlyConfigService configService) {
        this(configService, Clock.systemUTC());
    }

    DefaultRecurlyWebhookParser(final RecurlyConfigService configService, final Clock clock) {
        this.configService = configService;
        this.clock = clock;
    }

    @Override
    public NormalizedBillingEvent parse(final RawWebhook raw) throws BillingException {
        final long startedAt = System.nanoTime();
        if (raw == null) {
            logFailure(startedAt, "webhook_missing", null, 0, false);
            throw new TerminalBillingException("Recurly webhook is missing");
        }
        final int payloadChars = raw.payload() == null ? 0 : raw.payload().length();
        if (StringUtils.isBlank(raw.payload())) {
            logFailure(startedAt, "payload_missing", null, payloadChars, false);
            throw new TerminalBillingException("Recurly webhook payload is missing");
        }

        final String signature = StringUtils.defaultIfBlank(raw.signature(), header(raw.headers(), SIGNATURE_HEADER));
        try {
            verifySignature(signature, raw.payload());
        } catch (final BillingException e) {
            logFailure(startedAt, signatureFailureReason(e), null, payloadChars, false);
            throw e;
        }

        try {
            final JsonNode payload = objectMapper.readTree(raw.payload());
            final String objectType = text(payload, "object_type");
            final String eventType = text(payload, "event_type");
            final String uuid = text(payload, "uuid");
            final String eventTime = text(payload, "event_time");
            if (StringUtils.isBlank(eventTime)) {
                // Without it there is no ordering signal, and Instant.parse(null) would throw an NPE
                // straight through the SPI boundary, which the caller has no way to classify.
                // The signature did verify - saying otherwise here would put a malformed payload on the
                // same alert as a forged one.
                logFailure(startedAt, "event_time_missing", notificationId(payload, raw), payloadChars, true);
                throw new TerminalBillingException("Recurly webhook has no event_time");
            }
            final Instant occurredAt = Instant.parse(eventTime);
            final String resourceId = resourceId(payload, objectType, uuid);

            final Map<String, String> attributes = new HashMap<>();
            putIfNotBlank(attributes, "eventType", eventType);
            putIfNotBlank(attributes, "siteId", text(payload, "site_id"));
            putIfNotBlank(attributes, "resourceType", objectType);
            putIfNotBlank(attributes, "resourceId", resourceId);

            final String subscriptionId = "subscription".equals(objectType) && StringUtils.isNotBlank(uuid)
                    ? "uuid-" + uuid
                    : null;
            final BillingEventType normalizedType = mapEvent(objectType, eventType);
            final String eventId = notificationId(payload, raw);
            // Negative when Recurly's clock is ahead of ours; the sign is the skew signal, so it travels
            // as the value rather than as a second derived flag.
            final long lagMs = clock.instant().toEpochMilli() - occurredAt.toEpochMilli();
            webhookEvent()
                    .outcome(normalizedType == BillingEventType.UNKNOWN
                            ? ConnectorLogEvent.OUTCOME_IGNORED
                            : ConnectorLogEvent.OUTCOME_SUCCESS)
                    .durationSince(startedAt)
                    .field("error_class", ConnectorLogEvent.ERROR_CLASS_NONE)
                    .field("event_id", eventId)
                    .field("subscription_id", subscriptionId)
                    .field("vendor_event_type", eventType)
                    .field("object_type", objectType)
                    .field("normalized_event_type", normalizedType)
                    .field("resource_id", resourceId)
                    .field("webhook_lag_ms", Long.valueOf(lagMs))
                    .field("payload_chars", Integer.valueOf(payloadChars))
                    .field("signature_verified", Boolean.TRUE)
                    .info(LOG);
            return new NormalizedBillingEvent(BillingPlatform.RECURLY, normalizedType, eventId, subscriptionId,
                    text(payload, "account_code"), occurredAt, attributes);
        } catch (final JsonProcessingException | DateTimeException e) {
            logFailure(startedAt, "payload_parsing_failed", null, payloadChars, true);
            throw new TerminalBillingException("Malformed Recurly JSON webhook", e);
        }
    }

    private void logFailure(final long startedAt, final String reason, final String eventId, final int payloadChars,
                            final boolean signatureVerified) {
        webhookEvent()
                .outcome(ConnectorLogEvent.OUTCOME_FAILURE)
                .durationSince(startedAt)
                .field("error_class", ConnectorLogEvent.ERROR_CLASS_VALIDATION)
                .reason(reason)
                .field("event_id", eventId)
                .field("payload_chars", Integer.valueOf(payloadChars))
                .field("signature_verified", Boolean.valueOf(signatureVerified))
                .warn(LOG);
    }

    /**
     * Platform and operation are stated here as a fallback for a parser used on its own; when the
     * connector's scope is open its values win and the line reads identically.
     */
    private ConnectorLogEvent webhookEvent() {
        return ConnectorLogEvent.of(EVENT_WEBHOOK_PROCESSING)
                .platform(BillingPlatform.RECURLY)
                .operation("parse_webhook");
    }

    /**
     * The reason is carried by the exception rather than recovered from its wording. Matching on
     * {@code getMessage()} makes every reason label hostage to a copy edit: reword "invalid format" and
     * the dashboard silently starts counting a different bucket.
     */
    private static String signatureFailureReason(final BillingException error) {
        return error instanceof WebhookSignatureException signatureFailure
                ? signatureFailure.reason()
                : "signature_verification_failed";
    }

    /**
     * The event id the core deduplicates on. The body is preferred because the HMAC covers only
     * {@code timestamp + "." + payload} — a header is not signed, so taking it first would let the dedup
     * identity of a byte-identical, correctly-signed delivery be changed from outside. The header stays
     * as the fallback because Recurly omits {@code id} from some payload shapes.
     */
    protected String notificationId(final JsonNode payload, final RawWebhook raw) {
        return StringUtils.defaultIfBlank(text(payload, "id"), header(raw.headers(), NOTIFICATION_ID_HEADER));
    }

    protected void verifySignature(final String header, final String payload) throws BillingException {
        if (StringUtils.isBlank(header)) {
            throw new WebhookSignatureException("signature_missing", "Recurly webhook signature is missing");
        }
        final String[] parts = header.split(",");
        if (parts.length < 2 || StringUtils.isBlank(parts[0])) {
            throw new WebhookSignatureException("signature_format_invalid",
                    "Recurly webhook signature has an invalid format");
        }

        final Instant signedAt = parseTimestamp(parts[0]);
        if (Duration.between(signedAt, clock.instant()).abs().getSeconds() > configService.getWebhookToleranceSeconds()) {
            throw new WebhookSignatureException("signature_expired",
                    "Recurly webhook signature timestamp is outside the accepted tolerance");
        }

        try {
            final Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(configService.getWebhookSigningKey().getBytes(StandardCharsets.UTF_8),
                    HMAC_SHA_256));
            final byte[] expected = mac.doFinal((parts[0] + "." + payload).getBytes(StandardCharsets.UTF_8));
            for (int index = 1; index < parts.length; index++) {
                try {
                    if (MessageDigest.isEqual(expected, HexFormat.of().parseHex(parts[index].trim()))) {
                        return;
                    }
                } catch (final IllegalArgumentException ignored) {
                    // A malformed candidate does not prevent a second rotation signature from matching.
                }
            }
            throw new WebhookSignatureException("signature_invalid", "Recurly webhook signature is invalid");
        } catch (final GeneralSecurityException e) {
            throw new WebhookSignatureException("signature_verification_failed",
                    "Could not verify Recurly webhook signature", e);
        }
    }

    protected Instant parseTimestamp(final String value) throws TerminalBillingException {
        try {
            final long timestamp = Long.parseLong(value);
            return value.length() > 10 ? Instant.ofEpochMilli(timestamp) : Instant.ofEpochSecond(timestamp);
        } catch (final NumberFormatException | DateTimeException e) {
            throw new WebhookSignatureException("signature_timestamp_invalid",
                    "Recurly webhook signature timestamp is invalid");
        }
    }

    /**
     * A signature rejection that names its own reason. Still a {@link TerminalBillingException}, so
     * nothing outside this class has to know it exists - callers keep classifying it as they always did,
     * while the observability line gets a label that survives a reworded message.
     */
    protected static class WebhookSignatureException extends TerminalBillingException {
        private static final long serialVersionUID = 1L;

        private final String reason;

        public WebhookSignatureException(final String reason, final String message) {
            super(message);
            this.reason = reason;
        }

        public WebhookSignatureException(final String reason, final String message, final Throwable cause) {
            super(message, cause);
            this.reason = reason;
        }

        public String reason() {
            return reason;
        }
    }

    protected BillingEventType mapEvent(final String objectType, final String eventType) {
        if ("payment".equals(objectType)) {
            return switch (StringUtils.defaultString(eventType)) {
                case "succeeded" -> BillingEventType.PAYMENT_SUCCEEDED;
                case "failed" -> BillingEventType.PAYMENT_FAILED;
                default -> BillingEventType.UNKNOWN;
            };
        }
        if ("charge_invoice".equals(objectType)) {
            return switch (StringUtils.defaultString(eventType)) {
                case "paid" -> BillingEventType.INVOICE_PAID;
                case "past_due" -> BillingEventType.INVOICE_PAST_DUE;
                case "failed" -> BillingEventType.INVOICE_FAILED;
                default -> BillingEventType.UNKNOWN;
            };
        }
        if ("invoice".equals(objectType) && "past_due".equals(eventType)) {
            return BillingEventType.INVOICE_PAST_DUE;
        }
        if (!"subscription".equals(objectType)) {
            return BillingEventType.UNKNOWN;
        }
        return switch (StringUtils.defaultString(eventType)) {
            case "created" -> BillingEventType.SUBSCRIPTION_CREATED;
            case "updated" -> BillingEventType.SUBSCRIPTION_UPDATED;
            case "renewed" -> BillingEventType.SUBSCRIPTION_RENEWED;
            case "canceled" -> BillingEventType.SUBSCRIPTION_CANCELLED;
            case "expired" -> BillingEventType.SUBSCRIPTION_EXPIRED;
            case "paused" -> BillingEventType.SUBSCRIPTION_PAUSED;
            case "resumed", "reactivated" -> BillingEventType.SUBSCRIPTION_RESUMED;
            case "pending_change.scheduled" -> BillingEventType.SUBSCRIPTION_CHANGE_SCHEDULED;
            case "pause.scheduled" -> BillingEventType.SUBSCRIPTION_PAUSE_SCHEDULED;
            case "pause.modified" -> BillingEventType.SUBSCRIPTION_PAUSE_UPDATED;
            case "pause.canceled" -> BillingEventType.SUBSCRIPTION_PAUSE_CANCELLED;
            default -> BillingEventType.UNKNOWN;
        };
    }

    protected String resourceId(final JsonNode payload, final String objectType, final String uuid) {
        if ("payment".equals(objectType) && StringUtils.isNotBlank(uuid)) {
            return StringUtils.prependIfMissing(uuid, "uuid-");
        }
        if ("invoice".equals(objectType) || "charge_invoice".equals(objectType)) {
            final String invoiceNumber = text(payload, "invoice_number");
            return StringUtils.isBlank(invoiceNumber) ? null : StringUtils.prependIfMissing(invoiceNumber, "number-");
        }
        return null;
    }

    protected static String header(final Map<String, String> headers, final String name) {
        if (headers == null) {
            return null;
        }
        return headers.entrySet().stream().filter(entry -> name.equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue).findFirst().orElse(null);
    }

    protected static String text(final JsonNode node, final String field) {
        return node.path(field).asText(null);
    }

    protected static void putIfNotBlank(final Map<String, String> values, final String key, final String value) {
        if (StringUtils.isNotBlank(value)) {
            values.put(key, value);
        }
    }
}
