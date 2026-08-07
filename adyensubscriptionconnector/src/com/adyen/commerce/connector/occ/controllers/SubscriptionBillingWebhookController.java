/*
 *                        ######
 *                        ######
 *  ############    ####( ######  #####. ######  ############   ############
 *  #############  #####( ######  #####. ######  #############  #############
 *         ######  #####( ######  #####. ######  #####  ######  #####  ######
 *  ###### ######  #####( ######  #####. ######  #####  #####   #####  ######
 *  ###### ######  #####( ######  #####. ######  #####          #####  ######
 *  #############  #############  #############  #############  #####  ######
 *   ############   ############  #############   ############  #####  ######
 *                                       ######
 *                                #############
 *                                ############
 *
 *  Adyen Hybris Extension
 *
 *  Copyright (c) 2026 Adyen B.V.
 *  This file is open source and available under the MIT license.
 *  See the LICENSE file for more info.
 */
package com.adyen.commerce.connector.occ.controllers;

import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.adyen.commerce.connector.dto.RawWebhook;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.BillingException;
import com.adyen.commerce.connector.webhook.SubscriptionBillingWebhookDispatcher;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Public inbound webhook endpoint (task P1.10). Identifies the platform from the path, builds a
 * {@link RawWebhook} from the raw request, and hands it to the platform-agnostic
 * {@link SubscriptionBillingWebhookDispatcher}. Signature/auth verification is entirely
 * connector-owned (e.g. Chargebee's Basic Auth check lives in its {@code parseWebhook}) — this
 * controller does no verification of its own and is intentionally not {@code @Secured}, since
 * external billing platforms authenticate with their own per-platform scheme, not our OAuth2 client.
 */
@RestController
@RequestMapping("/subscription-billing/webhooks")
public class SubscriptionBillingWebhookController
{
	private static final Logger LOG = LoggerFactory.getLogger(SubscriptionBillingWebhookController.class);

	@Autowired
	private SubscriptionBillingWebhookDispatcher webhookDispatcher;

	@PostMapping("/{platform}")
	public ResponseEntity<String> receive(@PathVariable final String platform,
			@RequestBody(required = false) final String payload, final HttpServletRequest request)
	{
		final BillingPlatform billingPlatform;
		try
		{
			billingPlatform = BillingPlatform.valueOf(platform.toUpperCase(Locale.ROOT));
		}
		catch (final IllegalArgumentException e)
		{
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Unknown billing platform: " + platform);
		}

		final Map<String, String> headers = extractHeaders(request);
		final RawWebhook raw = new RawWebhook(headers, payload == null ? "" : payload, headers.get("Signature"));

		try
		{
			webhookDispatcher.dispatch(billingPlatform, raw);
			return ResponseEntity.ok("OK");
		}
		catch (final BillingException e)
		{
			LOG.warn("Webhook rejected for platform {}: {}", billingPlatform, e.getMessage());
			// Chargebee retries on any non-2xx regardless of the exact code (its own documented backoff),
			// so this distinction is for delivery-log readability, not to influence retry behavior.
			final HttpStatus status = e.isRetryable() ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_REQUEST;
			return ResponseEntity.status(status).body(e.getMessage());
		}
	}

	private static Map<String, String> extractHeaders(final HttpServletRequest request)
	{
		final Map<String, String> headers = new LinkedHashMap<>();
		final Enumeration<String> names = request.getHeaderNames();
		if (names != null)
		{
			while (names.hasMoreElements())
			{
				final String name = names.nextElement();
				headers.put(name, request.getHeader(name));
			}
		}
		return headers;
	}

	public void setWebhookDispatcher(final SubscriptionBillingWebhookDispatcher webhookDispatcher)
	{
		this.webhookDispatcher = webhookDispatcher;
	}
}
