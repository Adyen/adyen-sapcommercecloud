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
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.adyen.commerce.connector.chargebee.client.ChargebeeApiClient;
import com.adyen.commerce.connector.chargebee.client.ChargebeeSubscriptionParams;
import com.adyen.commerce.connector.chargebee.config.ChargebeeConfigService;
import com.adyen.commerce.connector.chargebee.http.ChargebeeHttpClient;
import com.adyen.commerce.connector.chargebee.http.ChargebeeHttpResponse;
import com.adyen.commerce.connector.chargebee.util.FormEncoder;
import com.adyen.commerce.connector.dto.BillingSubscriptionRef;
import com.adyen.commerce.connector.dto.CardMetadata;
import com.adyen.commerce.connector.dto.NormalizedSubscription;
import com.adyen.commerce.connector.dto.NormalizedSubscriptionStatus;
import com.adyen.commerce.connector.enums.BillingPlatform;
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
	/**
	 * Chargebee's answer when a request with this idempotency key is already being processed. Retryable
	 * rather than terminal - see {@link #isRetryable}.
	 */
	protected static final String ERROR_CODE_REQUEST_IN_PROGRESS = "invalid_state_for_request";

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
	public NormalizedSubscription fetchSubscription(final String subscriptionId) throws BillingException
	{
		final ChargebeeHttpResponse response = httpClient
				.get(configService.getApiBaseUrl() + "/subscriptions/" + pathSegment(subscriptionId), authHeader());
		requireSuccess(response, "retrieve subscription");
		return mapSubscription(response.body());
	}

	protected NormalizedSubscription mapSubscription(final String body) throws BillingException
	{
		final JsonNode root = readJson(body, "subscription");
		final String subscriptionId = readId(root, "subscription");
		final JsonNode subscription = root.path("subscription");
		final JsonNode planItem = findPlanItem(subscription);

		return new NormalizedSubscription(
				new BillingSubscriptionRef(BillingPlatform.CHARGEBEE, subscriptionId),
				mapStatus(subscription),
				planItem.path("item_price_id").asText(null),
				planItem.path("quantity").asInt(1),
				parseEpochSeconds(subscription.path("current_term_start")),
				parseEpochSeconds(subscription.path("current_term_end")),
				isCancelAtPeriodEnd(subscription),
				parseEpochSeconds(subscription.path("updated_at")));
	}

	/**
	 * Chargebee has no {@code past_due} lifecycle state — a subscription with unpaid invoices stays
	 * {@code active} and carries the dunning signal next to it as {@code due_invoices_count}, which ships
	 * inside the subscription resource itself. So unlike Recurly, where past-due has to be derived from a
	 * second call listing the account's past-due invoices, one retrieve answers both questions.
	 */
	protected NormalizedSubscriptionStatus mapStatus(final JsonNode subscription)
	{
		final NormalizedSubscriptionStatus lifecycleStatus = mapLifecycleStatus(subscription.path("status").asText(null));
		return isPastDueEligible(lifecycleStatus) && subscription.path("due_invoices_count").asInt(0) > 0
				? NormalizedSubscriptionStatus.PAST_DUE
				: lifecycleStatus;
	}

	protected NormalizedSubscriptionStatus mapLifecycleStatus(final String chargebeeStatus)
	{
		if (StringUtils.isBlank(chargebeeStatus))
		{
			return NormalizedSubscriptionStatus.UNKNOWN;
		}
		return switch (chargebeeStatus.toLowerCase(Locale.ROOT))
		{
			case "future" -> NormalizedSubscriptionStatus.PENDING;
			// in_trial is a live subscription that simply isn't being charged yet, and non_renewing keeps
			// serving the customer until the term ends. Both are ACTIVE; the pending end of a non_renewing
			// subscription travels as cancelAtPeriodEnd instead of prematurely reporting CANCELLED, which
			// would revoke entitlement the customer has already paid for.
			case "in_trial", "active", "non_renewing" -> NormalizedSubscriptionStatus.ACTIVE;
			case "paused" -> NormalizedSubscriptionStatus.PAUSED;
			// EXPIRED, not CANCELLED, and the word is worth explaining because Chargebee's own is different.
			// By the time a subscription reaches Chargebee's "cancelled" it has stopped serving the customer,
			// which is the one situation Recurly reports as "expired". One vocabulary, one word for it: a
			// consumer asking "has this ended?" must not have to know which platform answered. CANCELLED is
			// left unused by both shipped adapters — see NormalizedSubscriptionStatus.
			case "cancelled" -> NormalizedSubscriptionStatus.EXPIRED;
			// "transferred" (the subscription moved to another Chargebee site/entity) has no normalized
			// equivalent: it is neither cancelled nor expired here. It stays UNKNOWN rather than being guessed
			// at, so reconciliation leaves the local status alone instead of acting on an invented one.
			default -> NormalizedSubscriptionStatus.UNKNOWN;
		};
	}

	/**
	 * Only states in which Chargebee would still be collecting money can be past due. Reporting PAST_DUE for
	 * an already cancelled subscription that happens to carry a written-off invoice would resurrect dunning
	 * for a subscription nobody is billing any more.
	 */
	protected boolean isPastDueEligible(final NormalizedSubscriptionStatus status)
	{
		return status == NormalizedSubscriptionStatus.ACTIVE || status == NormalizedSubscriptionStatus.PAUSED;
	}

	protected boolean isCancelAtPeriodEnd(final JsonNode subscription)
	{
		// non_renewing IS the end-of-term cancellation. cancel_schedule_created_at additionally covers a
		// cancellation scheduled for a specific future date, which Chargebee can carry on a still-active one.
		return "non_renewing".equalsIgnoreCase(subscription.path("status").asText(null))
				|| subscription.path("cancel_schedule_created_at").asLong(0L) > 0L;
	}

	/**
	 * A Chargebee subscription carries addons and one-time charges in the same {@code subscription_items}
	 * array as the plan, so the plan is identified by {@code item_type}, not by position. That item price id
	 * is exactly what {@link #createSubscription} sent as {@code subscription_items[item_price_id][0]}, which
	 * keeps the plan round-trip symmetric.
	 */
	protected JsonNode findPlanItem(final JsonNode subscription)
	{
		final JsonNode items = subscription.path("subscription_items");
		for (final JsonNode item : items)
		{
			if ("plan".equalsIgnoreCase(item.path("item_type").asText(null)))
			{
				return item;
			}
		}
		return items.path(0);
	}

	/**
	 * Chargebee timestamps are UNIX epoch seconds, not ISO-8601. An absent timestamp must stay {@code null}:
	 * silently reading it as epoch 0 would date a subscription to 1970 and let the reconciliation staleness
	 * guard discard every later snapshot as "older".
	 */
	protected Instant parseEpochSeconds(final JsonNode node) throws TerminalBillingException
	{
		if (node == null || node.isMissingNode() || node.isNull())
		{
			return null;
		}
		if (node.isNumber())
		{
			return toInstant(node.asLong());
		}

		final String text = node.asText(null);
		if (StringUtils.isBlank(text))
		{
			return null;
		}
		try
		{
			return toInstant(Long.parseLong(text.trim()));
		}
		catch (final NumberFormatException exception)
		{
			throw new TerminalBillingException("Malformed Chargebee subscription timestamp '" + text + "'", exception);
		}
	}

	protected Instant toInstant(final long epochSeconds)
	{
		return epochSeconds <= 0L ? null : Instant.ofEpochSecond(epochSeconds);
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
		if (isRetryable(response))
		{
			return new RetryableBillingException(message);
		}
		return new TerminalBillingException(message);
	}

	/**
	 * Whether Chargebee is saying "not now" rather than "not ever".
	 *
	 * <p>Beyond throttling and server faults there is one conflict that has to be read this way:
	 * {@code 409 invalid_state_for_request} is Chargebee's own idempotency answering that a request
	 * carrying this idempotency key is <em>still in flight</em>. That is not a rejection - it means some
	 * other caller got there first and is finishing the very work this one wanted done. It happens
	 * routinely, because an order is announced by both the place-order path and Adyen's notification and
	 * the two can reach the connector at the same moment.</p>
	 *
	 * <p>Classifying it as terminal produced a dead letter announcing that the shopper was charged and has
	 * no subscription, seconds before the winning caller created exactly that subscription. Retried
	 * instead, the loser comes back after the backoff, finds the subscription reference the winner
	 * persisted, and returns it without calling Chargebee at all.</p>
	 *
	 * <p>Matched on the error code rather than on the status alone: other 409s are genuine conflicts about
	 * the state of a subscription, and retrying those only delays an unavoidable dead letter.</p>
	 */
	protected boolean isRetryable(final ChargebeeHttpResponse response)
	{
		if (response.statusCode() == 429 || response.statusCode() >= 500)
		{
			return true;
		}
		return response.statusCode() == 409 && ERROR_CODE_REQUEST_IN_PROGRESS.equals(errorCode(response.body()));
	}

	/**
	 * The {@code api_error_code} Chargebee returns, or {@code null} when the body is absent or not the
	 * error shape. Deliberately reads the code and not the message: the message is prose meant for a
	 * human and is not a contract, the code is.
	 */
	protected String errorCode(final String body)
	{
		if (StringUtils.isBlank(body))
		{
			return null;
		}
		try
		{
			return objectMapper.readTree(body).path("api_error_code").asText(null);
		}
		catch (final IOException e)
		{
			return null;
		}
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
			final String message = node.path("message").asText(null);
			final String code = node.path("api_error_code").asText(node.path("type").asText(null));
			if (message == null && code == null)
			{
				return null;
			}
			return (code == null ? "" : "[" + code + "] ") + StringUtils.defaultString(message);
		}
		catch (final IOException e)
		{
			return null;
		}
	}

	protected String readId(final String body, final String wrapperKey) throws BillingException
	{
		return readId(readJson(body, wrapperKey), wrapperKey);
	}

	protected String readId(final JsonNode root, final String wrapperKey) throws BillingException
	{
		final JsonNode id = root.path(wrapperKey).path("id");
		if (id.isMissingNode() || StringUtils.isBlank(id.asText(null)))
		{
			throw new TerminalBillingException("Chargebee response missing " + wrapperKey + ".id");
		}
		return id.asText();
	}

	protected JsonNode readJson(final String body, final String resource) throws TerminalBillingException
	{
		try
		{
			return objectMapper.readTree(body);
		}
		catch (final IOException e)
		{
			throw new TerminalBillingException("Malformed Chargebee " + resource + " response: " + e.getMessage());
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
}
