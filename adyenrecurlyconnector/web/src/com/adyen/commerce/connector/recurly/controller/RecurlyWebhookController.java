package com.adyen.commerce.connector.recurly.controller;

import java.util.Map;

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
        final Map<String, String> singleHeaders = headers.toSingleValueMap();
        final RawWebhook raw = new RawWebhook(singleHeaders, payload, headers.getFirst("recurly-signature"));
        try
        {
            webhookDispatcher.dispatch(BillingPlatform.RECURLY, raw);
            return ResponseEntity.noContent().build();
        }
        catch (final RetryableBillingException e)
        {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        catch (final BillingException e)
        {
            return ResponseEntity.badRequest().build();
        }
    }
}
