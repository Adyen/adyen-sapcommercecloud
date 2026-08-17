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
package com.adyen.commerce.connector.chargebee.client.impl;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.adyen.commerce.connector.chargebee.client.ChargebeeApiClient;
import com.adyen.commerce.connector.chargebee.client.ChargebeeSubscriptionParams;
import com.adyen.commerce.connector.chargebee.config.ChargebeeConfigService;
import com.adyen.commerce.connector.chargebee.http.ChargebeeHttpClient;
import com.adyen.commerce.connector.chargebee.http.ChargebeeHttpResponse;
import com.adyen.commerce.connector.chargebee.util.FormEncoder;
import com.adyen.commerce.connector.dto.CardMetadata;
import com.adyen.commerce.connector.exception.BillingException;
import com.adyen.commerce.connector.exception.PreconditionFailedException;
import com.adyen.commerce.connector.exception.RetryableBillingException;
import com.adyen.commerce.connector.exception.TerminalBillingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Default Chargebee client. See {@link ChargebeeApiClient} for the contract.
 */
public class DefaultChargebeeApiClient implements ChargebeeApiClient
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultChargebeeApiClient.class);
	private final ObjectMapper objectMapper = new ObjectMapper();

	private ChargebeeHttpClient httpClient;
	private ChargebeeConfigService configService;

	@Override
	public String ensureCustomer(final String customerId, final String email, final String firstName,
			final String lastName) throws BillingException
	{
		final String base = configService.getApiBaseUrl();
		final String auth = authHeader();

		if (StringUtils.isNotBlank(customerId))
		{
			final ChargebeeHttpResponse existing = httpClient.get(base + "/customers/" + pathSegment(customerId), auth);
			if (existing.statusCode() == 200)
			{
				return readId(existing.body(), "customer");
			}
			if (existing.statusCode() != 404)
			{
				throw toBillingException(existing, "retrieve customer");
			}
		}

		final Map<String, String> params = new LinkedHashMap<>();
		putIfNotBlank(params, "id", customerId);
		putIfNotBlank(params, "email", email);
		putIfNotBlank(params, "first_name", firstName);
		putIfNotBlank(params, "last_name", lastName);

		final ChargebeeHttpResponse response = httpClient.post(base + "/customers", auth, FormEncoder.encode(params),
				customerId);
		requireSuccess(response, "create customer");
		return readId(response.body(), "customer");
	}

	@Override
	public String importPermanentToken(final String customerId, final String referenceId, final CardMetadata card)
			throws BillingException
	{
		final Map<String, String> params = new LinkedHashMap<>();
		params.put("customer_id", customerId);
		params.put("type", "card");
		putIfNotBlank(params, "gateway_account_id", configService.getGatewayAccountId());
		params.put("reference_id", referenceId);
		params.put("replace_primary_payment_source", "true");
		if (card != null)
		{
			// card[brand] intentionally omitted: Adyen scheme codes (mc/amex/...) are not Chargebee's
			// card[brand] enum and only matter for skip_retrieval (Vantiv), so sending them risks a 400.
			putIfNotBlank(params, "card[last4]", card.last4());
			addExpiry(params, card.expiry());
		}

		// reference_id is a deterministic idempotency key: a replay returns the original payment source.
		final ChargebeeHttpResponse response = httpClient.post(
				configService.getApiBaseUrl() + "/payment_sources/create_using_permanent_token", authHeader(),
				FormEncoder.encode(params), referenceId);
		requireSuccess(response, "import Adyen token");
		return readId(response.body(), "payment_source");
	}

	@Override
	public String createSubscription(final ChargebeeSubscriptionParams params) throws BillingException
	{
		final Map<String, String> form = new LinkedHashMap<>();
		putIfNotBlank(form, "id", params.subscriptionId());
		form.put("subscription_items[item_price_id][0]", params.itemPriceId());
		form.put("subscription_items[quantity][0]", String.valueOf(Math.max(1, params.quantity())));
		form.put("auto_collection", "on");
		if (params.startEpochSeconds() != null)
		{
			form.put("start_date", String.valueOf(params.startEpochSeconds()));
		}
		if (params.metadata() != null)
		{
			for (final Map.Entry<String, String> entry : params.metadata().entrySet())
			{
				putIfNotBlank(form, "meta_data[" + entry.getKey() + "]", entry.getValue());
			}
		}

		final ChargebeeHttpResponse response = httpClient.post(
				configService.getApiBaseUrl() + "/customers/" + pathSegment(params.customerId()) + "/subscription_for_items",
				authHeader(), FormEncoder.encode(form), params.subscriptionId());
		requireSuccess(response, "create subscription");
		return readId(response.body(), "subscription");
	}

	@Override
	public void updateSubscription(final String subscriptionId, final String itemPriceId, final Integer quantity)
			throws BillingException
	{
		if (StringUtils.isBlank(itemPriceId) && quantity == null)
		{
			throw new PreconditionFailedException(
					"updateSubscription called with nothing to change for subscription '" + subscriptionId + "'");
		}
		// Chargebee's update_for_items is item-based: subscription_items[quantity][0] is meaningless without
		// subscription_items[item_price_id][0] to say WHICH item's quantity changes. Sending quantity alone
		// yields "subscription_items[item_price_id][0] : cannot be blank" (HTTP 400). Fail fast with a clear
		// precondition so callers pass the (unchanged) item price alongside a quantity change.
		if (quantity != null && StringUtils.isBlank(itemPriceId))
		{
			throw new PreconditionFailedException("Chargebee update_for_items requires an item price id when changing "
					+ "quantity for subscription '" + subscriptionId + "'");
		}

		final Map<String, String> form = new LinkedHashMap<>();
		if (StringUtils.isNotBlank(itemPriceId))
		{
			form.put("subscription_items[item_price_id][0]", itemPriceId);
		}
		if (quantity != null)
		{
			form.put("subscription_items[quantity][0]", String.valueOf(quantity.intValue()));
		}

		final ChargebeeHttpResponse response = httpClient.post(
				configService.getApiBaseUrl() + "/subscriptions/" + pathSegment(subscriptionId) + "/update_for_items",
				authHeader(), FormEncoder.encode(form), null);
		requireSuccess(response, "update subscription");
	}

	@Override
	public void cancelSubscription(final String subscriptionId, final boolean atPeriodEnd) throws BillingException
	{
		final Map<String, String> form = new LinkedHashMap<>();
		form.put("cancel_option", atPeriodEnd ? "end_of_term" : "immediately");

		final ChargebeeHttpResponse response = httpClient.post(
				configService.getApiBaseUrl() + "/subscriptions/" + pathSegment(subscriptionId) + "/cancel_for_items",
				authHeader(), FormEncoder.encode(form), null);
		requireSuccess(response, "cancel subscription");
	}

	protected String authHeader() throws BillingException
	{
		final String encoded = Base64.getEncoder()
				.encodeToString((configService.getApiKey() + ":").getBytes(StandardCharsets.UTF_8));
		return "Basic " + encoded;
	}

	protected void requireSuccess(final ChargebeeHttpResponse response, final String action) throws BillingException
	{
		if (!response.isSuccess())
		{
			throw toBillingException(response, action);
		}
	}

	protected BillingException toBillingException(final ChargebeeHttpResponse response, final String action)
	{
		final String detail = extractError(response.body());
		final String message = "Chargebee " + action + " failed (HTTP " + response.statusCode() + ")"
				+ (detail == null ? "" : ": " + detail);
		final boolean retryable = response.statusCode() == 429 || response.statusCode() >= 500;
		LOG.warn("event=vendor_api_error platform=CHARGEBEE operation={} outcome=failure http_status={} "
				+ "error_class={} vendor_error_code={} retryable={} correlation_id={}", action.replace(' ', '_'),
				response.statusCode(), classifyStatus(response.statusCode()), detail, retryable, correlationId());
		if (retryable)
		{
			return new RetryableBillingException(message);
		}
		return new TerminalBillingException(message);
	}

	protected String extractError(final String body)
	{
		if (StringUtils.isBlank(body))
		{
			return null;
		}
		try
		{
			final JsonNode node = objectMapper.readTree(body);
			final String code = node.path("api_error_code").asText(node.path("type").asText(null));
			return code;
		}
		catch (final IOException e)
		{
			return null;
		}
	}

	protected String readId(final String body, final String wrapperKey) throws BillingException
	{
		try
		{
			final JsonNode id = objectMapper.readTree(body).path(wrapperKey).path("id");
			if (id.isMissingNode() || StringUtils.isBlank(id.asText(null)))
			{
				throw new TerminalBillingException("Chargebee response missing " + wrapperKey + ".id");
			}
			return id.asText();
		}
		catch (final IOException e)
		{
			throw new TerminalBillingException("Malformed Chargebee response", e);
		}
	}

	protected void addExpiry(final Map<String, String> params, final String expiry)
	{
		if (StringUtils.isBlank(expiry))
		{
			return;
		}
		final String[] parts = expiry.split("/");
		if (parts.length == 2)
		{
			putIfNotBlank(params, "card[expiry_month]", StringUtils.stripStart(parts[0].trim(), "0"));
			putIfNotBlank(params, "card[expiry_year]", parts[1].trim());
		}
	}

	protected static void putIfNotBlank(final Map<String, String> params, final String key, final String value)
	{
		if (StringUtils.isNotBlank(value))
		{
			params.put(key, value);
		}
	}

	protected static String pathSegment(final String value)
	{
		return URLEncoder.encode(StringUtils.defaultString(value), StandardCharsets.UTF_8);
	}

	public void setHttpClient(final ChargebeeHttpClient httpClient)
	{
		this.httpClient = httpClient;
	}

	public void setConfigService(final ChargebeeConfigService configService)
	{
		this.configService = configService;
	}

	private static String classifyStatus(final int status)
	{
		if (status == 429) return "rate_limit";
		if (status >= 500) return "remote_5xx";
		if (status >= 400) return "remote_4xx";
		return "unexpected_status";
	}

	private static String correlationId()
	{
		return StringUtils.defaultIfBlank(MDC.get("correlationId"), "none");
	}
}
