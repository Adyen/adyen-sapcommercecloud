package com.adyen.commerce.connector.recurly.controller;

import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.adyen.commerce.connector.dto.RawWebhook;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.BillingException;
import com.adyen.commerce.connector.exception.RetryableBillingException;
import com.adyen.commerce.connector.webhook.SubscriptionBillingWebhookDispatcher;

/** Receives signed Recurly JSON notifications and delegates normalization/reconciliation to the SPI. */
@Controller
@RequestMapping("/webhooks/recurly")
public class RecurlyWebhookController
{
    private static final Logger LOG = LoggerFactory.getLogger(RecurlyWebhookController.class);
    private static final String CORRELATION_ID_KEY = "correlationId";
    private final SubscriptionBillingWebhookDispatcher webhookDispatcher;

    public RecurlyWebhookController(
            @Qualifier("subscriptionBillingWebhookDispatcher")
            final SubscriptionBillingWebhookDispatcher webhookDispatcher)
    {
        this.webhookDispatcher = webhookDispatcher;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> receive(@RequestHeader final HttpHeaders headers, @RequestBody final String payload)
    {
        final long startedAt = System.nanoTime();
        final String previousCorrelationId = MDC.get(CORRELATION_ID_KEY);
        final String correlationId = previousCorrelationId == null ? UUID.randomUUID().toString() : previousCorrelationId;
        MDC.put(CORRELATION_ID_KEY, correlationId);
        final Map<String, String> singleHeaders = headers.toSingleValueMap();
        final RawWebhook raw = new RawWebhook(singleHeaders, payload, headers.getFirst("recurly-signature"));
        LOG.info("event=webhook_request platform=RECURLY operation=dispatch_webhook outcome=received duration_ms=0 "
                        + "http_status=none error_class=none retryable=false correlation_id={} payload_bytes={} "
                        + "signature_present={}", correlationId, payloadBytes(payload),
                headers.getFirst("recurly-signature") != null);
        try
        {
            webhookDispatcher.dispatch(BillingPlatform.RECURLY, raw);
            LOG.info("event=webhook_request platform=RECURLY operation=dispatch_webhook outcome=success duration_ms={} "
                            + "http_status=204 error_class=none retryable=false correlation_id={} payload_bytes={}",
                    elapsedMillis(startedAt), correlationId, payloadBytes(payload));
            return ResponseEntity.noContent().build();
        }
        catch (final RetryableBillingException e)
        {
            LOG.warn("event=webhook_request platform=RECURLY operation=dispatch_webhook outcome=failure duration_ms={} "
                            + "http_status=503 error_class=retryable_billing exception_class={} retryable=true "
                            + "correlation_id={} payload_bytes={}", elapsedMillis(startedAt), e.getClass().getName(),
                    correlationId, payloadBytes(payload));
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        catch (final BillingException e)
        {
            LOG.warn("event=webhook_request platform=RECURLY operation=dispatch_webhook outcome=failure duration_ms={} "
                            + "http_status=400 error_class=terminal_billing exception_class={} retryable=false "
                            + "correlation_id={} payload_bytes={}", elapsedMillis(startedAt), e.getClass().getName(),
                    correlationId, payloadBytes(payload));
            return ResponseEntity.badRequest().build();
        }
        finally
        {
            if (previousCorrelationId == null)
            {
                MDC.remove(CORRELATION_ID_KEY);
            }
            else
            {
                MDC.put(CORRELATION_ID_KEY, previousCorrelationId);
            }
        }
    }

    private static long elapsedMillis(final long startedAt)
    {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private static int payloadBytes(final String payload)
    {
        return payload == null ? 0 : payload.getBytes(StandardCharsets.UTF_8).length;
    }
}
