package com.adyen.commerce.connector.recurly.webhook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adyen.commerce.connector.dto.BillingEventType;
import com.adyen.commerce.connector.dto.NormalizedBillingEvent;
import com.adyen.commerce.connector.dto.RawWebhook;
import com.adyen.commerce.connector.exception.TerminalBillingException;
import com.adyen.commerce.connector.recurly.config.RecurlyConfigService;

import de.hybris.bootstrap.annotations.UnitTest;

@UnitTest
public class DefaultRecurlyWebhookParserTest
{
    private static final Instant NOW = Instant.parse("2026-07-21T10:00:00Z");
    private static final String SECRET = "webhook-secret";
    private static final String PAYLOAD = "{\"id\":\"notice-1\",\"object_type\":\"subscription\","
            + "\"site_id\":\"site-1\",\"event_type\":\"canceled\","
            + "\"event_time\":\"2026-07-21T10:00:00Z\","
            + "\"uuid\":\"63ab531e1d5b1d47eaf1ef44eeb853c3\"}";

    @Mock
    private RecurlyConfigService configService;

    private DefaultRecurlyWebhookParser parser;

    @Before
    public void setUp() throws Exception
    {
        MockitoAnnotations.openMocks(this);
        parser = new DefaultRecurlyWebhookParser(configService, Clock.fixed(NOW, ZoneOffset.UTC));
        when(configService.getWebhookSigningKey()).thenReturn(SECRET);
        when(configService.getWebhookToleranceSeconds()).thenReturn(300);
    }

    @Test
    public void validSignedSubscriptionEventIsNormalized() throws Exception
    {
        final String timestamp = Long.toString(NOW.getEpochSecond());
        final String signature = timestamp + "," + sign(timestamp, PAYLOAD);

        final NormalizedBillingEvent event = parser.parse(new RawWebhook(
                Map.of("Recurly-Notification-Id", "header-notice"), PAYLOAD, signature));

        assertEquals(BillingEventType.SUBSCRIPTION_CANCELLED, event.type());
        assertEquals("uuid-63ab531e1d5b1d47eaf1ef44eeb853c3", event.externalSubscriptionId());
        assertEquals("header-notice", event.attributes().get("notificationId"));
    }

    @Test
    public void alteredPayloadIsRejected() throws Exception
    {
        final String timestamp = Long.toString(NOW.getEpochSecond());
        final String signature = timestamp + "," + sign(timestamp, PAYLOAD);

        assertThrows(TerminalBillingException.class,
                () -> parser.parse(new RawWebhook(Map.of(), PAYLOAD + " ", signature)));
    }

    @Test
    public void staleSignatureIsRejected() throws Exception
    {
        final String timestamp = Long.toString(NOW.minusSeconds(301).getEpochSecond());
        final String signature = timestamp + "," + sign(timestamp, PAYLOAD);

        assertThrows(TerminalBillingException.class,
                () -> parser.parse(new RawWebhook(Map.of(), PAYLOAD, signature)));
    }

    private String sign(final String timestamp, final String payload) throws Exception
    {
        final Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8)));
    }
}
