package com.adyen.commerce.connector.recurly.webhook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

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
        // The signed body wins over the unsigned header: the HMAC covers only timestamp + "." + payload,
        // so a header-first rule would let the dedup identity be changed from outside the signature.
        assertEquals("notice-1", event.eventId());
    }

    @Test
    public void notificationHeaderIsUsedOnlyWhenTheBodyCarriesNoId() throws Exception
    {
        final String payload = PAYLOAD.replace("\"id\":\"notice-1\",", "");
        final String timestamp = Long.toString(NOW.getEpochSecond());
        final String signature = timestamp + "," + sign(timestamp, payload);

        final NormalizedBillingEvent event = parser.parse(new RawWebhook(
                Map.of("Recurly-Notification-Id", "header-notice"), payload, signature));

        assertEquals("header-notice", event.eventId());
    }

    @Test
    public void payloadWithoutEventTimeIsRejectedAsTerminal() throws Exception
    {
        final String payload = PAYLOAD.replace("\"event_time\":\"2026-07-21T10:00:00Z\",", "");
        final String timestamp = Long.toString(NOW.getEpochSecond());
        final String signature = timestamp + "," + sign(timestamp, payload);
        final RawWebhook raw = new RawWebhook(Map.of(), payload, signature);

        // Without a timestamp there is no ordering signal at all; it must fail as a classified billing
        // error rather than as an NPE crossing the SPI boundary.
        assertThrows(TerminalBillingException.class, () -> parser.parse(raw));
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

    @Test
    public void successfulPaymentIsNormalizedForApiEnrichment() throws Exception
    {
        final String payload = "{\"id\":\"notice-2\",\"object_type\":\"payment\",\"event_type\":\"succeeded\","
                + "\"event_time\":\"2026-07-21T10:00:00Z\",\"uuid\":\"payment-uuid\"}";
        final NormalizedBillingEvent event = parse(payload);

        assertEquals(BillingEventType.PAYMENT_SUCCEEDED, event.type());
        assertNull(event.externalSubscriptionId());
        assertEquals("payment", event.attributes().get("resourceType"));
        assertEquals("uuid-payment-uuid", event.attributes().get("resourceId"));
        // Pinned because the map used to carry the object type twice: RecurlySubscriptionBillingConnector
        // reads resourceType/resourceId, and a second copy under another key drifts out of use unnoticed.
        assertEquals(Set.of("eventType", "resourceType", "resourceId"), event.attributes().keySet());
    }

    @Test
    public void failedChargeInvoiceIsNormalizedForApiEnrichment() throws Exception
    {
        final String payload = "{\"id\":\"notice-3\",\"object_type\":\"charge_invoice\",\"event_type\":\"past_due\","
                + "\"event_time\":\"2026-07-21T10:00:00Z\",\"invoice_number\":1031}";
        final NormalizedBillingEvent event = parse(payload);

        assertEquals(BillingEventType.INVOICE_PAST_DUE, event.type());
        assertEquals("charge_invoice", event.attributes().get("resourceType"));
        assertEquals("number-1031", event.attributes().get("resourceId"));
    }

    @Test
    public void unknownEventRemainsAcknowledgable() throws Exception
    {
        final String payload = "{\"id\":\"notice-4\",\"object_type\":\"account\",\"event_type\":\"updated\","
                + "\"event_time\":\"2026-07-21T10:00:00Z\"}";
        assertEquals(BillingEventType.UNKNOWN, parse(payload).type());
    }

    @Test
    public void subscriptionLifecycleEventsRemainSemanticallyDistinct() throws Exception
    {
        assertSubscriptionEvent("created", BillingEventType.SUBSCRIPTION_CREATED);
        assertSubscriptionEvent("updated", BillingEventType.SUBSCRIPTION_UPDATED);
        assertSubscriptionEvent("renewed", BillingEventType.SUBSCRIPTION_RENEWED);
        assertSubscriptionEvent("canceled", BillingEventType.SUBSCRIPTION_CANCELLED);
        assertSubscriptionEvent("expired", BillingEventType.SUBSCRIPTION_EXPIRED);
        assertSubscriptionEvent("paused", BillingEventType.SUBSCRIPTION_PAUSED);
        assertSubscriptionEvent("resumed", BillingEventType.SUBSCRIPTION_RESUMED);
        assertSubscriptionEvent("reactivated", BillingEventType.SUBSCRIPTION_RESUMED);
    }

    @Test
    public void scheduledSubscriptionChangesAreRecognized() throws Exception
    {
        assertSubscriptionEvent("pending_change.scheduled", BillingEventType.SUBSCRIPTION_CHANGE_SCHEDULED);
        assertSubscriptionEvent("pause.scheduled", BillingEventType.SUBSCRIPTION_PAUSE_SCHEDULED);
        assertSubscriptionEvent("pause.modified", BillingEventType.SUBSCRIPTION_PAUSE_UPDATED);
        assertSubscriptionEvent("pause.canceled", BillingEventType.SUBSCRIPTION_PAUSE_CANCELLED);
    }

    @Test
    public void paymentAndInvoiceEventsAreNotCollapsedIntoSubscriptionStatusClaims() throws Exception
    {
        assertEquals(BillingEventType.PAYMENT_FAILED,
                parse(resourcePayload("payment", "failed", "\"uuid\":\"payment-uuid\"")).type());
        assertEquals(BillingEventType.INVOICE_PAID,
                parse(resourcePayload("charge_invoice", "paid", "\"invoice_number\":1031")).type());
        assertEquals(BillingEventType.INVOICE_FAILED,
                parse(resourcePayload("charge_invoice", "failed", "\"invoice_number\":1031")).type());
        assertEquals(BillingEventType.INVOICE_PAST_DUE,
                parse(resourcePayload("invoice", "past_due", "\"invoice_number\":1031")).type());
    }

    private void assertSubscriptionEvent(final String eventType, final BillingEventType expected) throws Exception
    {
        final String payload = resourcePayload("subscription", eventType, "\"uuid\":\"subscription-uuid\"");
        final NormalizedBillingEvent event = parse(payload);
        assertEquals(expected, event.type());
        assertEquals("uuid-subscription-uuid", event.externalSubscriptionId());
    }

    private String resourcePayload(final String objectType, final String eventType, final String resourceField)
    {
        return "{\"id\":\"notice-" + objectType + "-" + eventType + "\",\"object_type\":\""
                + objectType + "\",\"event_type\":\"" + eventType
                + "\",\"event_time\":\"2026-07-21T10:00:00Z\"," + resourceField + "}";
    }

    private NormalizedBillingEvent parse(final String payload) throws Exception
    {
        final String timestamp = Long.toString(NOW.getEpochSecond());
        return parser.parse(new RawWebhook(Map.of(), payload, timestamp + "," + sign(timestamp, payload)));
    }

    private String sign(final String timestamp, final String payload) throws Exception
    {
        final Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8)));
    }
}
