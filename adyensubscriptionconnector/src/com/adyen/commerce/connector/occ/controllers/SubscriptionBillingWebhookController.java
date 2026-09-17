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
 * Public inbound webhook endpoint:
 * {@code POST /subscription-billing/webhooks/{baseSiteId}/{platform}}. Identifies the platform from the
 * path, builds a {@link RawWebhook} from the raw request, and hands it to the platform-agnostic
 * {@link SubscriptionBillingWebhookDispatcher}. Signature and auth verification is entirely
 * connector-owned (Chargebee's Basic Auth check lives in its {@code parseWebhook}), so this controller
 * verifies nothing itself and is intentionally not {@code @Secured}: external billing platforms
 * authenticate with their own per-platform scheme rather than the OAuth2 client.
 *
 * <p>The base site is in the path rather than derived from the payload because the connectors read their
 * credentials from the current base store while the first thing they do with the payload is authenticate
 * it; resolving the store from an unauthenticated body would invert that order. OCC's
 * {@code baseSiteMatchingFilter} is bypassed for this prefix (see subscriptionbillingocc-web-spring.xml),
 * so the site is activated here explicitly.</p>
 */
@RestController
@RequestMapping("/subscription-billing/webhooks")
public class SubscriptionBillingWebhookController
{
	private static final Logger LOG = LoggerFactory.getLogger(SubscriptionBillingWebhookController.class);

	/**
	 * Fixed bodies: nothing the caller sends is echoed back. The endpoint is public and unauthenticated, so
	 * reflecting a path variable or an exception message would hand an anonymous caller a probe oracle and
	 * a reflected-XSS sink, the body being rendered as whatever the request's Accept header asks for. The
	 * detail goes to the log instead.
	 */
	private static final String UNKNOWN_PLATFORM_BODY = "Unknown billing platform";
	private static final String UNKNOWN_BASE_SITE_BODY = "Unknown base site";
	private static final String REJECTED_BODY = "Webhook rejected";
	private static final String TEMPORARILY_UNAVAILABLE_BODY = "Webhook temporarily unavailable";

	/** Cap on how much of an untrusted value reaches the log, and a guard against CRLF forging log lines. */
	private static final int MAX_LOGGED_VALUE_LENGTH = 100;

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
			LOG.warn("Webhook for unknown billing platform [{}] rejected", forLog(platform));
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(UNKNOWN_PLATFORM_BODY);
		}

		// The connectors read their credentials off the current base store and this request carries no
		// session, so the site has to be activated first. The lookup is caught as well as null-checked:
		// the endpoint is public, so an unknown uid answers 404 rather than a 500 from
		// UnknownIdentifierException.
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
		// Signature stays null: the scheme is connector-owned and each platform names its header
		// differently — Recurly sends "recurly-signature", Chargebee signs nothing and uses Basic Auth —
		// so each connector reads the header it expects out of the raw headers.
		final RawWebhook raw = new RawWebhook(headers, payload == null ? "" : payload, null);

		try
		{
			webhookDispatcher.dispatch(billingPlatform, raw);
			return ResponseEntity.ok("OK");
		}
		catch (final BillingException e)
		{
			LOG.warn("Webhook rejected for platform {}: {}", billingPlatform, e.getMessage());
			// Chargebee retries on any non-2xx regardless of the exact code, so this distinction is for
			// delivery-log readability rather than to steer retries. The message stays out of the response:
			// a reason such as "bad signature" tells an anonymous caller about the configuration and tells
			// the platform, which acts only on the status code, nothing.
			return e.isRetryable()
					? ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(TEMPORARILY_UNAVAILABLE_BODY)
					: ResponseEntity.status(HttpStatus.BAD_REQUEST).body(REJECTED_BODY);
		}
	}

	/**
	 * Renders an untrusted request value safe to put in a log line: newlines and carriage returns would let a
	 * caller forge extra entries, and an unbounded path variable would let them flood the log.
	 */
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
	 * {@code BillingPlatform.valueOf} cannot be used to validate: BillingPlatform is a dynamic enum whose
	 * generated {@code valueOf} mints and caches a new instance for any string instead of throwing, so
	 * junk names from an anonymous caller would grow the enum cache without bound. Asking the type service
	 * keeps this in step with the values declared in adyensubscriptionconnector-items.xml.
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
