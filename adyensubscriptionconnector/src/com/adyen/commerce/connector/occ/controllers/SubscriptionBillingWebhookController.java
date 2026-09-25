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
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import de.hybris.platform.basecommerce.model.site.BaseSiteModel;
import de.hybris.platform.enumeration.EnumerationService;
import de.hybris.platform.servicelayer.exceptions.UnknownIdentifierException;
import de.hybris.platform.site.BaseSiteService;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Public inbound webhook endpoint {@code POST /subscription-billing/webhooks/{baseSiteId}/{platform}}. Not
 * {@code @Secured}: each connector verifies its platform's own signature or credentials while parsing.
 *
 * <p>The base site comes from the path, because the connector needs its store's credentials to authenticate
 * the payload before trusting anything in it. OCC's {@code baseSiteMatchingFilter} skips this prefix, so the
 * site is activated here.</p>
 */
@RestController
@RequestMapping("/subscription-billing/webhooks")
public class SubscriptionBillingWebhookController
{
	private static final Logger LOG = LoggerFactory.getLogger(SubscriptionBillingWebhookController.class);

	/** Fixed bodies: the endpoint is public, so nothing the caller sent is echoed back. */
	private static final String UNKNOWN_PLATFORM_BODY = "Unknown billing platform";
	private static final String UNKNOWN_BASE_SITE_BODY = "Unknown base site";
	private static final String REJECTED_BODY = "Webhook rejected";
	private static final String TEMPORARILY_UNAVAILABLE_BODY = "Webhook temporarily unavailable";

	/** Cap on how much of an untrusted value reaches the log. */
	private static final int MAX_LOGGED_VALUE_LENGTH = 100;

	@Resource(name = "subscriptionBillingWebhookDispatcher")
	private SubscriptionBillingWebhookDispatcher webhookDispatcher;

	@Resource(name = "baseSiteService")
	private BaseSiteService baseSiteService;

	@Resource(name = "enumerationService")
	private EnumerationService enumerationService;

	@PostMapping("/{baseSiteId}/{platform}")
	public ResponseEntity<String> receive(@PathVariable final String baseSiteId, @PathVariable final String platform,
			@RequestBody(required = false) final String payload, final HttpServletRequest request)
	{
		final BillingPlatform billingPlatform = resolvePlatform(platform);
		if (billingPlatform == null)
		{
			LOG.warn("Webhook for unknown billing platform [{}] rejected", forLog(platform));
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(UNKNOWN_PLATFORM_BODY);
		}

		// An unknown uid answers 404 rather than a 500 from UnknownIdentifierException.
		final BaseSiteModel baseSite;
		try
		{
			baseSite = baseSiteService.getBaseSiteForUID(baseSiteId);
		}
		catch (final UnknownIdentifierException e)
		{
			LOG.warn("Webhook for unknown base site [{}] rejected", forLog(baseSiteId));
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(UNKNOWN_BASE_SITE_BODY);
		}
		if (baseSite == null)
		{
			LOG.warn("Webhook for unknown base site [{}] rejected", forLog(baseSiteId));
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(UNKNOWN_BASE_SITE_BODY);
		}
		baseSiteService.setCurrentBaseSite(baseSite, false);

		final Map<String, String> headers = extractHeaders(request);
		// Signature stays null: each connector reads its own header from the raw headers.
		final RawWebhook raw = new RawWebhook(headers, payload == null ? "" : payload, null);

		try
		{
			webhookDispatcher.dispatch(billingPlatform, raw);
			return ResponseEntity.ok("OK");
		}
		catch (final BillingException e)
		{
			LOG.warn("Webhook rejected for platform {}: {}", billingPlatform, e.getMessage());
			// Platforms retry on any non-2xx; the status only makes their delivery logs readable.
			return e.isRetryable()
					? ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(TEMPORARILY_UNAVAILABLE_BODY)
					: ResponseEntity.status(HttpStatus.BAD_REQUEST).body(REJECTED_BODY);
		}
	}

	/** Strips line breaks, which would forge log entries, and caps the length of an untrusted value. */
	private static String forLog(final String value)
	{
		if (value == null)
		{
			return "null";
		}
		final String singleLine = value.replaceAll("[\\r\\n]", "_");
		return singleLine.length() > MAX_LOGGED_VALUE_LENGTH
				? singleLine.substring(0, MAX_LOGGED_VALUE_LENGTH) + "..."
				: singleLine;
	}

	/**
	 * Not {@code BillingPlatform.valueOf}: on a dynamic enum it creates and caches a value for any string.
	 *
	 * @return the matching platform, or {@code null} when no such value is declared
	 */
	protected BillingPlatform resolvePlatform(final String platform)
	{
		if (platform == null)
		{
			return null;
		}
		return enumerationService.<BillingPlatform> getEnumerationValues(BillingPlatform._TYPECODE).stream()
				.filter(value -> platform.equalsIgnoreCase(value.getCode()))
				.findFirst()
				.orElse(null);
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

	public void setBaseSiteService(final BaseSiteService baseSiteService)
	{
		this.baseSiteService = baseSiteService;
	}

	public void setEnumerationService(final EnumerationService enumerationService)
	{
		this.enumerationService = enumerationService;
	}
}
