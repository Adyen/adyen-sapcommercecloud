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
import org.slf4j.MDC;

import com.adyen.commerce.connector.dto.BillingEventType;
import com.adyen.commerce.connector.dto.NormalizedBillingEvent;
import com.adyen.commerce.connector.dto.RawWebhook;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.BillingException;
import com.adyen.commerce.connector.exception.TerminalBillingException;
import com.adyen.commerce.connector.recurly.config.RecurlyConfigService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Verifies and normalizes Recurly's signed JSON webhook format.
 */
public class DefaultRecurlyWebhookParser implements RecurlyWebhookParser {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultRecurlyWebhookParser.class);
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
            logFailure(startedAt, "webhook_missing", "none", 0);
            throw new TerminalBillingException("Recurly webhook is missing");
        }
        final int payloadBytes = raw.payload() == null ? 0 : raw.payload().getBytes(StandardCharsets.UTF_8).length;
        if (StringUtils.isBlank(raw.payload())) {
            logFailure(startedAt, "payload_missing", "none", payloadBytes);
            throw new TerminalBillingException("Recurly webhook payload is missing");
        }

        final String signature = StringUtils.defaultIfBlank(raw.signature(), header(raw.headers(), SIGNATURE_HEADER));
        try {
            verifySignature(signature, raw.payload());
        } catch (final BillingException e) {
            logFailure(startedAt, signatureFailureReason(e), "none", payloadBytes);
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
                logFailure(startedAt, "event_time_missing", notificationId(payload, raw), payloadBytes);
                throw new TerminalBillingException("Recurly webhook has no event_time");
            }
            final Instant occurredAt = Instant.parse(eventTime);
            final String resourceId = resourceId(payload, objectType, uuid);

            final Map<String, String> attributes = new HashMap<>();
            putIfNotBlank(attributes, "eventType", eventType);
            putIfNotBlank(attributes, "objectType", objectType);
            putIfNotBlank(attributes, "siteId", text(payload, "site_id"));
            putIfNotBlank(attributes, "resourceType", objectType);
            putIfNotBlank(attributes, "resourceId", resourceId);

            final String subscriptionId = "subscription".equals(objectType) && StringUtils.isNotBlank(uuid)
                    ? "uuid-" + uuid
                    : null;
            final BillingEventType normalizedType = mapEvent(objectType, eventType);
            final String eventId = notificationId(payload, raw);
            final long lagMs = clock.instant().toEpochMilli() - occurredAt.toEpochMilli();
            LOG.info("event=webhook_processing platform=RECURLY operation=parse_webhook outcome={} duration_ms={} "
                            + "error_class=none reason=none event_id={} subscription_id={} "
                            + "vendor_event_type={} object_type={} normalized_event_type={} resource_id={} "
                            + "webhook_lag_ms={} clock_skew={} payload_bytes={} signature_verified=true",
                    normalizedType == BillingEventType.UNKNOWN ? "ignored" : "success", elapsedMillis(startedAt),
                     eventId, subscriptionId, eventType, objectType, normalizedType, resourceId,
                    lagMs, lagMs < 0L, payloadBytes);
            return new NormalizedBillingEvent(BillingPlatform.RECURLY, normalizedType, eventId, subscriptionId,
                    text(payload, "account_code"), occurredAt, attributes);
        } catch (final JsonProcessingException | DateTimeException e) {
            logFailure(startedAt, "payload_parsing_failed", "none", payloadBytes);
            throw new TerminalBillingException("Malformed Recurly JSON webhook", e);
        }
    }

    private void logFailure(final long startedAt, final String reason, final String eventId, final int payloadBytes) {
        LOG.warn("event=webhook_processing platform=RECURLY operation=parse_webhook outcome=failure duration_ms={} "
                        + "error_class=validation reason={} event_id={} payload_bytes={} "
                        + "signature_verified=false", elapsedMillis(startedAt), reason, eventId,
                payloadBytes);
    }

    private static String signatureFailureReason(final BillingException error) {
        final String message = StringUtils.defaultString(error.getMessage());
        if (message.contains("missing")) return "signature_missing";
        if (message.contains("timestamp") && message.contains("tolerance")) return "signature_expired";
        if (message.contains("timestamp")) return "signature_timestamp_invalid";
        if (message.contains("format")) return "signature_format_invalid";
        if (message.contains("invalid")) return "signature_invalid";
        return "signature_verification_failed";
    }

    private static long elapsedMillis(final long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
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
            throw new TerminalBillingException("Recurly webhook signature is missing");
        }
        final String[] parts = header.split(",");
        if (parts.length < 2 || StringUtils.isBlank(parts[0])) {
            throw new TerminalBillingException("Recurly webhook signature has an invalid format");
        }

        final Instant signedAt = parseTimestamp(parts[0]);
        if (Duration.between(signedAt, clock.instant()).abs().getSeconds() > configService.getWebhookToleranceSeconds()) {
            throw new TerminalBillingException("Recurly webhook signature timestamp is outside the accepted tolerance");
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
            throw new TerminalBillingException("Recurly webhook signature is invalid");
        } catch (final GeneralSecurityException e) {
            throw new TerminalBillingException("Could not verify Recurly webhook signature", e);
        }
    }

    protected Instant parseTimestamp(final String value) throws TerminalBillingException {
        try {
            final long timestamp = Long.parseLong(value);
            return value.length() > 10 ? Instant.ofEpochMilli(timestamp) : Instant.ofEpochSecond(timestamp);
        } catch (final NumberFormatException | DateTimeException e) {
            throw new TerminalBillingException("Recurly webhook signature timestamp is invalid");
        }
    }

    protected BillingEventType mapEvent(final String objectType, final String eventType) {
        if ("payment".equals(objectType)) {
            return switch (StringUtils.defaultString(eventType)) {
                case "succeeded" -> BillingEventType.INVOICE_PAID;
                case "failed" -> BillingEventType.INVOICE_PAYMENT_FAILED;
                default -> BillingEventType.UNKNOWN;
            };
        }
        if ("charge_invoice".equals(objectType)) {
            return switch (StringUtils.defaultString(eventType)) {
                case "paid" -> BillingEventType.INVOICE_PAID;
                case "failed", "past_due" -> BillingEventType.INVOICE_PAYMENT_FAILED;
                default -> BillingEventType.UNKNOWN;
            };
        }
        if ("invoice".equals(objectType) && "past_due".equals(eventType)) {
            return BillingEventType.INVOICE_PAYMENT_FAILED;
        }
        if (!"subscription".equals(objectType)) {
            return BillingEventType.UNKNOWN;
        }
        return switch (StringUtils.defaultString(eventType)) {
            case "created" -> BillingEventType.SUBSCRIPTION_ACTIVATED;
            case "renewed" -> BillingEventType.SUBSCRIPTION_RENEWED;
            case "canceled", "expired" -> BillingEventType.SUBSCRIPTION_CANCELLED;
            case "paused" -> BillingEventType.SUBSCRIPTION_PAUSED;
            case "resumed", "reactivated" -> BillingEventType.SUBSCRIPTION_RESUMED;
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
