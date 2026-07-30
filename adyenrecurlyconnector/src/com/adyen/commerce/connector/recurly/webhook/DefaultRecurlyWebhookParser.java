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

/** Verifies and normalizes Recurly's signed JSON webhook format. */
public class DefaultRecurlyWebhookParser implements RecurlyWebhookParser
{
    private static final String SIGNATURE_HEADER = "recurly-signature";
    private static final String NOTIFICATION_ID_HEADER = "recurly-notification-id";
    private static final String HMAC_SHA_256 = "HmacSHA256";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock clock;
    private final RecurlyConfigService configService;

    public DefaultRecurlyWebhookParser(final RecurlyConfigService configService)
    {
        this(configService, Clock.systemUTC());
    }

    DefaultRecurlyWebhookParser(final RecurlyConfigService configService, final Clock clock)
    {
        this.configService = configService;
        this.clock = clock;
    }

    @Override
    public NormalizedBillingEvent parse(final RawWebhook raw) throws BillingException
    {
        if (raw == null)
        {
            throw new TerminalBillingException("Recurly webhook is missing");
        }

        final String signature = StringUtils.defaultIfBlank(raw.signature(), header(raw.headers(), SIGNATURE_HEADER));
        verifySignature(signature, raw.payload());

        try
        {
            final JsonNode payload = objectMapper.readTree(raw.payload());
            final String objectType = text(payload, "object_type");
            final String eventType = text(payload, "event_type");
            final String uuid = text(payload, "uuid");
            final Instant occurredAt = Instant.parse(text(payload, "event_time"));

            final Map<String, String> attributes = new HashMap<>();
            putIfNotBlank(attributes, "eventType", eventType);
            putIfNotBlank(attributes, "objectType", objectType);
            putIfNotBlank(attributes, "siteId", text(payload, "site_id"));
            putIfNotBlank(attributes, "notificationId", StringUtils.defaultIfBlank(
                    header(raw.headers(), NOTIFICATION_ID_HEADER), text(payload, "id")));

            final String subscriptionId = "subscription".equals(objectType) && StringUtils.isNotBlank(uuid)
                    ? "uuid-" + uuid
                    : null;
            return new NormalizedBillingEvent(BillingPlatform.RECURLY, mapEvent(objectType, eventType),
                    subscriptionId, null, occurredAt, attributes);
        }
        catch (final JsonProcessingException | DateTimeException e)
        {
            throw new TerminalBillingException("Malformed Recurly JSON webhook: " + e.getMessage());
        }
    }

    protected void verifySignature(final String header, final String payload) throws BillingException
    {
        if (StringUtils.isBlank(header))
        {
            throw new TerminalBillingException("Recurly webhook signature is missing");
        }
        final String[] parts = header.split(",");
        if (parts.length < 2 || StringUtils.isBlank(parts[0]))
        {
            throw new TerminalBillingException("Recurly webhook signature has an invalid format");
        }

        final Instant signedAt = parseTimestamp(parts[0]);
        if (Duration.between(signedAt, clock.instant()).abs().getSeconds() > configService.getWebhookToleranceSeconds())
        {
            throw new TerminalBillingException("Recurly webhook signature timestamp is outside the accepted tolerance");
        }

        try
        {
            final Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(configService.getWebhookSigningKey().getBytes(StandardCharsets.UTF_8),
                    HMAC_SHA_256));
            final byte[] expected = mac.doFinal((parts[0] + "." + payload).getBytes(StandardCharsets.UTF_8));
            for (int index = 1; index < parts.length; index++)
            {
                try
                {
                    if (MessageDigest.isEqual(expected, HexFormat.of().parseHex(parts[index].trim())))
                    {
                        return;
                    }
                }
                catch (final IllegalArgumentException ignored)
                {
                    // A malformed candidate does not prevent a second rotation signature from matching.
                }
            }
            throw new TerminalBillingException("Recurly webhook signature is invalid");
        }
        catch (final GeneralSecurityException e)
        {
            throw new TerminalBillingException("Could not verify Recurly webhook signature: " + e.getMessage());
        }
    }

    protected Instant parseTimestamp(final String value) throws TerminalBillingException
    {
        try
        {
            final long timestamp = Long.parseLong(value);
            return value.length() > 10 ? Instant.ofEpochMilli(timestamp) : Instant.ofEpochSecond(timestamp);
        }
        catch (final NumberFormatException | DateTimeException e)
        {
            throw new TerminalBillingException("Recurly webhook signature timestamp is invalid");
        }
    }

    protected BillingEventType mapEvent(final String objectType, final String eventType)
    {
        if (!"subscription".equals(objectType))
        {
            return BillingEventType.UNKNOWN;
        }
        return switch (StringUtils.defaultString(eventType))
        {
            case "created" -> BillingEventType.SUBSCRIPTION_ACTIVATED;
            case "renewed" -> BillingEventType.SUBSCRIPTION_RENEWED;
            case "canceled", "expired" -> BillingEventType.SUBSCRIPTION_CANCELLED;
            case "paused" -> BillingEventType.SUBSCRIPTION_PAUSED;
            case "resumed", "reactivated" -> BillingEventType.SUBSCRIPTION_RESUMED;
            default -> BillingEventType.UNKNOWN;
        };
    }

    protected static String header(final Map<String, String> headers, final String name)
    {
        if (headers == null)
        {
            return null;
        }
        return headers.entrySet().stream().filter(entry -> name.equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue).findFirst().orElse(null);
    }

    protected static String text(final JsonNode node, final String field)
    {
        return node.path(field).asText(null);
    }

    protected static void putIfNotBlank(final Map<String, String> values, final String key, final String value)
    {
        if (StringUtils.isNotBlank(value))
        {
            values.put(key, value);
        }
    }
}
