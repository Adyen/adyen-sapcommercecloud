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

import de.hybris.platform.basecommerce.model.site.BaseSiteModel;
import de.hybris.platform.enumeration.EnumerationService;
import de.hybris.platform.servicelayer.exceptions.UnknownIdentifierException;
import de.hybris.platform.site.BaseSiteService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Public inbound webhook endpoint (task P1.10):
 * {@code POST /subscription-billing/webhooks/{baseSiteId}/{platform}}. Identifies the platform from the
 * path, builds a {@link RawWebhook} from the raw request, and hands it to the platform-agnostic
 * {@link SubscriptionBillingWebhookDispatcher}. Signature/auth verification is entirely
 * connector-owned (e.g. Chargebee's Basic Auth check lives in its {@code parseWebhook}) — this
 * controller does no verification of its own and is intentionally not {@code @Secured}, since
 * external billing platforms authenticate with their own per-platform scheme, not our OAuth2 client.
 *
 * <p>The base site is in the path rather than derived from the payload because the connectors read their
 * credentials from the current base store, and the very first thing a connector does with the payload is
 * authenticate it. Resolving the store from an as-yet-unauthenticated body would invert that order, so
 * the caller has to name the site. OCC's {@code baseSiteMatchingFilter} is not what resolves it — that
 * filter is deliberately bypassed for this prefix (see subscriptionbillingocc-web-spring.xml), so the
 * site is activated here explicitly.</p>
 */
@RestController
@RequestMapping("/subscription-billing/webhooks")
public class SubscriptionBillingWebhookController
{
	private static final Logger LOG = LoggerFactory.getLogger(SubscriptionBillingWebhookController.class);

	@Autowired
	private SubscriptionBillingWebhookDispatcher webhookDispatcher;

	@Autowired
	private BaseSiteService baseSiteService;

	@Autowired
	private EnumerationService enumerationService;

	@PostMapping("/{baseSiteId}/{platform}")
	public ResponseEntity<String> receive(@PathVariable final String baseSiteId, @PathVariable final String platform,
			@RequestBody(required = false) final String payload, final HttpServletRequest request)
	{
		final BillingPlatform billingPlatform = resolvePlatform(platform);
		if (billingPlatform == null)
		{
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Unknown billing platform: " + platform);
		}

		// The connectors read their credentials off the current base store, and this request carries no
		// session. Without activating the site first, every webhook fails with "No current base store".
		// The lookup is caught as well as null-checked: this endpoint is public and unauthenticated, so an
		// unknown uid has to come back as 404 rather than as a 500 from an UnknownIdentifierException.
		final BaseSiteModel baseSite;
		try
		{
			baseSite = baseSiteService.getBaseSiteForUID(baseSiteId);
		}
		catch (final UnknownIdentifierException e)
		{
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Unknown base site: " + baseSiteId);
		}
		if (baseSite == null)
		{
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Unknown base site: " + baseSiteId);
		}
		baseSiteService.setCurrentBaseSite(baseSite, false);

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

	/**
	 * {@code BillingPlatform.valueOf} cannot be used to validate: BillingPlatform is a dynamic enum, whose
	 * generated {@code valueOf} mints and caches a new instance for ANY string instead of throwing. Left to
	 * it, an unknown platform would reach the dispatcher as a 400 rather than a 404, and repeated requests
	 * with junk names would grow the enum cache without bound. Asking the type system keeps this in step
	 * with the values declared in adyensubscriptionconnector-items.xml.
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
