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
package com.adyen.commerce.connector.chargebee;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
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
import com.adyen.commerce.connector.chargebee.plan.ChargebeePlanResolver;
import com.adyen.commerce.connector.dto.AdyenTokenHandle;
import com.adyen.commerce.connector.dto.BillingCustomerRef;
import com.adyen.commerce.connector.dto.BillingEventType;
import com.adyen.commerce.connector.dto.BillingPaymentMethodRef;
import com.adyen.commerce.connector.dto.BillingSubscriptionRef;
import com.adyen.commerce.connector.dto.ConnectorCapabilities;
import com.adyen.commerce.connector.dto.CustomerSyncRequest;
import com.adyen.commerce.connector.dto.NormalizedBillingEvent;
import com.adyen.commerce.connector.dto.PlanRef;
import com.adyen.commerce.connector.dto.PlanResolutionRequest;
import com.adyen.commerce.connector.dto.RawWebhook;
import com.adyen.commerce.connector.dto.SubscriptionCancelRequest;
import com.adyen.commerce.connector.dto.SubscriptionCreateRequest;
import com.adyen.commerce.connector.dto.SubscriptionUpdateRequest;
import com.adyen.commerce.connector.dto.TokenImportRequest;
import com.adyen.commerce.connector.dto.TokenImportStyle;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.BillingException;
import com.adyen.commerce.connector.exception.PreconditionFailedException;
import com.adyen.commerce.connector.exception.TerminalBillingException;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Chargebee adapter of the {@link SubscriptionBillingConnector} SPI (ADR-001 Option A: Adyen keeps
 * processing recurring payments; Chargebee only orchestrates billing).
 *
 * <p>This first cut covers the outbound lifecycle (customer, token import, plan resolution,
 * subscription create/update/cancel) plus inbound webhook verification/normalization (task P2.4).
 * Pause is not supported ({@code supportsPause=false}, SPI default rejects it).</p>
 */
public class ChargebeeSubscriptionBillingConnector implements SubscriptionBillingConnector
{
	private static final Logger LOG = LoggerFactory.getLogger(ChargebeeSubscriptionBillingConnector.class);
	private static final ConnectorCapabilities CAPABILITIES = new ConnectorCapabilities(
			false, // requiresNetworkTransactionId — the Adyen plugin never captures an NTID, and Chargebee import does not need one
			true,  // supportsImmediateStart — subscription_for_items can start immediately
			false, // supportsPause — deferred to a later increment (SPI default rejects pause)
			true,  // requiresPreConfiguredPlan — the item price must already exist in the Chargebee catalog
			true,  // liveTokenValidationOnImport — create_using_permanent_token makes a live retrieval call to Adyen
			TokenImportStyle.SLASH_JOINED); // reference_id = shopperReference/recurringDetailReference

	private static final String AUTHORIZATION_HEADER = "Authorization";
	private static final String BASIC_PREFIX = "Basic ";

	private final ObjectMapper objectMapper = new ObjectMapper();

	private ChargebeeApiClient apiClient;
	private ChargebeeConfigService configService;
	private ChargebeePlanResolver planResolver;

	@Override
	public BillingPlatform platform()
	{
		return BillingPlatform.CHARGEBEE;
	}

	@Override
	public ConnectorCapabilities capabilities()
	{
		return CAPABILITIES;
	}

	@Override
	public String configuredAdyenMerchantAccount()
	{
		return configService.getConfiguredAdyenMerchantAccount();
	}

	@Override
	public BillingCustomerRef ensureCustomer(final CustomerSyncRequest request) throws BillingException
	{
		final long startedAt = System.nanoTime();
		final String customerId;
		try
		{
			customerId = apiClient.ensureCustomer(request.customerId(), request.email(), request.firstName(),
					request.lastName());
		}
		catch (final BillingException e)
		{
			LOG.warn("event=connector_operation platform=CHARGEBEE operation=ensure_customer outcome=failure "
					+ "duration_ms={} error_class={} exception_class={} correlation_id={}", elapsedMillis(startedAt),
					errorClass(e), e.getClass().getName(), correlationId());
			throw e;
		}
		LOG.info("event=connector_operation platform=CHARGEBEE operation=ensure_customer outcome=success duration_ms={} "
				+ "error_class=none correlation_id={}", elapsedMillis(startedAt), correlationId());
		return new BillingCustomerRef(BillingPlatform.CHARGEBEE, customerId);
	}

	@Override
	public BillingPaymentMethodRef importAdyenToken(final TokenImportRequest request) throws BillingException
	{
		final long startedAt = System.nanoTime();
		final AdyenTokenHandle token = request.token();
		LOG.info("event=connector_operation platform=CHARGEBEE operation=import_token outcome=started correlation_id={} "
						+ "token_reference={} merchant_account={}", correlationId(), token.storedPaymentMethodId(),
				token.merchantAccount());
		verifyMerchantAccount(token);
		final String paymentSourceId;
		try
		{
			paymentSourceId = apiClient.importPermanentToken(request.customer().externalId(), buildReferenceId(token),
					token.cardMetadata());
		}
		catch (final BillingException e)
		{
			LOG.warn("event=connector_operation platform=CHARGEBEE operation=import_token outcome=failure duration_ms={} "
					+ "error_class={} correlation_id={} token_reference={} merchant_account={}", elapsedMillis(startedAt),
					errorClass(e), correlationId(), token.storedPaymentMethodId(), token.merchantAccount());
			throw e;
		}
		LOG.info("event=connector_operation platform=CHARGEBEE operation=import_token outcome=success duration_ms={} "
				+ "error_class=none correlation_id={} token_reference={} payment_source_id={} merchant_account={}",
				elapsedMillis(startedAt), correlationId(), token.storedPaymentMethodId(), paymentSourceId,
				token.merchantAccount());
		return new BillingPaymentMethodRef(BillingPlatform.CHARGEBEE, paymentSourceId);
	}

	@Override
	public PlanRef resolvePlan(final PlanResolutionRequest request) throws BillingException
	{
		final long startedAt = System.nanoTime();
		final PlanRef plan;
		try
		{
			plan = planResolver.resolve(request);
		}
		catch (final BillingException e)
		{
			LOG.warn("event=connector_operation platform=CHARGEBEE operation=resolve_plan outcome=failure "
					+ "duration_ms={} error_class={} exception_class={} correlation_id={} product_code={}",
					elapsedMillis(startedAt), errorClass(e), e.getClass().getName(), correlationId(), request.productCode());
			throw e;
		}
		LOG.info("event=connector_operation platform=CHARGEBEE operation=resolve_plan outcome=success duration_ms={} "
				+ "error_class=none correlation_id={} product_code={} plan_id={}", elapsedMillis(startedAt),
				correlationId(), request.productCode(), plan.planId());
		return plan;
	}

	@Override
	public BillingSubscriptionRef createSubscription(final SubscriptionCreateRequest request) throws BillingException
	{
		final long startedAt = System.nanoTime();
		try
		{
			return createSubscriptionInternal(request, startedAt);
		}
		catch (final BillingException e)
		{
			LOG.warn("event=connector_operation platform=CHARGEBEE operation=create_subscription outcome=failure "
					+ "duration_ms={} error_class={} exception_class={} correlation_id={} plan_id={} "
					+ "payment_source_id={}", elapsedMillis(startedAt), errorClass(e), e.getClass().getName(),
					correlationId(), itemPriceId(request.plan()), request.paymentMethod().externalId());
			throw e;
		}
	}

	private BillingSubscriptionRef createSubscriptionInternal(final SubscriptionCreateRequest request,
			final long startedAt) throws BillingException
	{
		final Long startEpochSeconds = request.startDate() == null ? null : request.startDate().getEpochSecond();
		final ChargebeeSubscriptionParams params = new ChargebeeSubscriptionParams(request.customer().externalId(),
				itemPriceId(request.plan()), request.quantity(), startEpochSeconds, request.idempotencyKey(),
				request.metadata());
		final String subscriptionId = apiClient.createSubscription(params);
		LOG.info("event=connector_operation platform=CHARGEBEE operation=create_subscription outcome=success "
				+ "duration_ms={} error_class=none correlation_id={} subscription_id={} plan_id={} quantity={} "
				+ "start_epoch_seconds={} payment_source_id={}", elapsedMillis(startedAt), correlationId(), subscriptionId,
				itemPriceId(request.plan()), request.quantity(), startEpochSeconds, request.paymentMethod().externalId());
		return new BillingSubscriptionRef(BillingPlatform.CHARGEBEE, subscriptionId);
	}

	@Override
	public void updateSubscription(final SubscriptionUpdateRequest request) throws BillingException
	{
		final long startedAt = System.nanoTime();
		final String itemPriceId = request.plan() == null ? null : itemPriceId(request.plan());
		try
		{
			apiClient.updateSubscription(request.subscription().externalId(), itemPriceId, request.quantity());
		}
		catch (final BillingException e)
		{
			LOG.warn("event=connector_operation platform=CHARGEBEE operation=update_subscription outcome=failure "
					+ "duration_ms={} error_class={} exception_class={} correlation_id={} subscription_id={} "
					+ "plan_id={} quantity={}", elapsedMillis(startedAt), errorClass(e), e.getClass().getName(),
					correlationId(), request.subscription().externalId(), itemPriceId, request.quantity());
			throw e;
		}
		LOG.info("event=connector_operation platform=CHARGEBEE operation=update_subscription outcome=success "
				+ "duration_ms={} error_class=none correlation_id={} subscription_id={} plan_id={} quantity={}",
				elapsedMillis(startedAt), correlationId(), request.subscription().externalId(), itemPriceId,
				request.quantity());
	}

	@Override
	public void cancelSubscription(final SubscriptionCancelRequest request) throws BillingException
	{
		final long startedAt = System.nanoTime();
		try
		{
			apiClient.cancelSubscription(request.subscription().externalId(), request.atPeriodEnd());
		}
		catch (final BillingException e)
		{
			LOG.warn("event=connector_operation platform=CHARGEBEE operation=cancel_subscription outcome=failure "
					+ "duration_ms={} error_class={} exception_class={} correlation_id={} subscription_id={} "
					+ "at_period_end={}", elapsedMillis(startedAt), errorClass(e), e.getClass().getName(),
					correlationId(), request.subscription().externalId(), request.atPeriodEnd());
			throw e;
		}
		LOG.info("event=connector_operation platform=CHARGEBEE operation=cancel_subscription outcome=success "
				+ "duration_ms={} error_class=none correlation_id={} subscription_id={} at_period_end={}",
				elapsedMillis(startedAt), correlationId(), request.subscription().externalId(), request.atPeriodEnd());
	}

	// pauseSubscription is intentionally NOT overridden: supportsPause=false, so the SPI default
	// throws CapabilityUnsupportedException. Chargebee pause/resume will be added in a later increment.

	@Override
	public NormalizedBillingEvent parseWebhook(final RawWebhook raw) throws BillingException
	{
		final long startedAt = System.nanoTime();
		if (raw == null)
		{
			logWebhookFailure(startedAt, "webhook_missing", "none", 0);
			throw new TerminalBillingException("Chargebee webhook is missing");
		}
		final int payloadBytes = raw.payload() == null ? 0 : raw.payload().getBytes(StandardCharsets.UTF_8).length;
		if (StringUtils.isBlank(raw.payload()))
		{
			logWebhookFailure(startedAt, "payload_missing", "none", payloadBytes);
			throw new TerminalBillingException("Chargebee webhook payload is missing");
		}
		try
		{
			verifyWebhookAuth(raw);
		}
		catch (final BillingException e)
		{
			logWebhookFailure(startedAt, webhookAuthFailureReason(e), "none", payloadBytes);
			throw e;
		}

		final JsonNode root;
		try
		{
			root = objectMapper.readTree(raw.payload());
		}
		catch (final IOException e)
		{
			logWebhookFailure(startedAt, "payload_parsing_failed", "none", payloadBytes);
			throw new TerminalBillingException("Chargebee webhook payload is not valid JSON", e);
		}

		final String chargebeeEventType = root.path("event_type").asText(null);
		final BillingEventType type = mapEventType(chargebeeEventType);
		if (type == null)
		{
			// Chargebee fires many event types we don't act on (invoice_generated, customer_changed, ...).
			// Acknowledge without erroring: the dispatcher no-ops on a null event.
			LOG.info("event=webhook_processing platform=CHARGEBEE operation=parse_webhook outcome=ignored "
					+ "duration_ms={} error_class=none reason=unsupported_event_type correlation_id={} event_id={} "
					+ "vendor_event_type={} payload_bytes={} auth_verified=true", elapsedMillis(startedAt), correlationId(),
					root.path("id").asText(null), chargebeeEventType, payloadBytes);
			return null;
		}

		final JsonNode content = root.path("content");
		final String externalSubscriptionId = firstNonBlank(
				content.path("subscription").path("id").asText(null),
				content.path("transaction").path("subscription_id").asText(null),
				content.path("invoice").path("subscription_id").asText(null));
		final String externalCustomerId = firstNonBlank(
				content.path("customer").path("id").asText(null),
				content.path("subscription").path("customer_id").asText(null),
				content.path("transaction").path("customer_id").asText(null),
				content.path("invoice").path("customer_id").asText(null));

		final long occurredAtEpochSeconds = root.path("occurred_at").asLong(0L);
		final Instant occurredAt = occurredAtEpochSeconds > 0 ? Instant.ofEpochSecond(occurredAtEpochSeconds) : Instant.now();

		// Chargebee's own docs recommend deduplicating on the event id; the core does exactly that, so it
		// travels as a first-class field rather than an attribute.
		final String eventId = root.path("id").asText(null);
		final long lagMs = Instant.now().toEpochMilli() - occurredAt.toEpochMilli();
		LOG.info("event=webhook_processing platform=CHARGEBEE operation=parse_webhook outcome=success duration_ms={} "
				+ "error_class=none reason=none correlation_id={} event_id={} subscription_id={} "
				+ "vendor_event_type={} normalized_event_type={} webhook_lag_ms={} clock_skew={} payload_bytes={} "
				+ "auth_verified=true", elapsedMillis(startedAt), correlationId(), eventId, externalSubscriptionId,
				chargebeeEventType, type, lagMs, lagMs < 0L, payloadBytes);
		if (StringUtils.isBlank(externalSubscriptionId))
		{
			LOG.warn("event=reconciliation_gap platform=CHARGEBEE operation=parse_webhook outcome=unresolved "
					+ "error_class=none correlation_id={} event_id={} vendor_event_type={} reason=subscription_id_missing",
					correlationId(), eventId, chargebeeEventType);
		}

		final Map<String, String> attributes = new LinkedHashMap<>();
		putIfNotBlank(attributes, "chargebeeEventType", chargebeeEventType);

		return new NormalizedBillingEvent(platform(), type, eventId, externalSubscriptionId, externalCustomerId,
				occurredAt, attributes);
	}

	/**
	 * Chargebee webhooks have no HMAC/signature scheme — Basic Auth on the receiving endpoint is the
	 * entire verification mechanism (credentials configured in Chargebee: Settings &gt; Webhooks &gt;
	 * "protected by basic authentication"). Fails closed: missing config, missing/malformed header, or a
	 * credential mismatch are all rejected, never silently accepted.
	 */
	protected void verifyWebhookAuth(final RawWebhook raw) throws BillingException
	{
		final String expectedUsername = configService.getWebhookUsername();
		final String expectedPassword = configService.getWebhookPassword();
		if (StringUtils.isBlank(expectedUsername) || StringUtils.isBlank(expectedPassword))
		{
			throw new PreconditionFailedException("Chargebee webhook Basic Auth credentials "
					+ "(chargebee.webhookUsername/chargebee.webhookPassword) are not configured");
		}

		final String authorizationHeader = findHeaderIgnoreCase(raw.headers(), AUTHORIZATION_HEADER);
		if (StringUtils.isBlank(authorizationHeader) || !authorizationHeader.startsWith(BASIC_PREFIX))
		{
			throw new TerminalBillingException("Chargebee webhook is missing a valid Basic Authorization header");
		}

		final String decoded;
		try
		{
			decoded = new String(Base64.getDecoder().decode(authorizationHeader.substring(BASIC_PREFIX.length())),
					StandardCharsets.UTF_8);
		}
		catch (final IllegalArgumentException e)
		{
			throw new TerminalBillingException("Chargebee webhook Authorization header is not valid Base64", e);
		}

		final int colonIndex = decoded.indexOf(':');
		final String actualUsername = colonIndex >= 0 ? decoded.substring(0, colonIndex) : decoded;
		final String actualPassword = colonIndex >= 0 ? decoded.substring(colonIndex + 1) : "";

		if (!constantTimeEquals(expectedUsername, actualUsername) || !constantTimeEquals(expectedPassword, actualPassword))
		{
			throw new TerminalBillingException("Chargebee webhook Basic Auth credentials do not match");
		}
	}

	/**
	 * Maps a Chargebee {@code event_type} to the normalized vocabulary. Unrecognized types return
	 * {@code null} (see {@link #parseWebhook}) rather than throwing, since Chargebee sends many event
	 * types this connector doesn't act on.
	 */
	protected BillingEventType mapEventType(final String chargebeeEventType)
	{
		if (chargebeeEventType == null)
		{
			return null;
		}
		switch (chargebeeEventType)
		{
			case "subscription_activated":
				return BillingEventType.SUBSCRIPTION_ACTIVATED;
			case "subscription_cancelled":
				return BillingEventType.SUBSCRIPTION_CANCELLED;
			case "payment_succeeded":
				return BillingEventType.INVOICE_PAID;
			case "payment_failed":
				return BillingEventType.INVOICE_PAYMENT_FAILED;
			default:
				return null;
		}
	}

	private static String firstNonBlank(final String... values)
	{
		for (final String value : values)
		{
			if (StringUtils.isNotBlank(value))
			{
				return value;
			}
		}
		return null;
	}

	private static void putIfNotBlank(final Map<String, String> map, final String key, final String value)
	{
		if (StringUtils.isNotBlank(value))
		{
			map.put(key, value);
		}
	}

	private static String findHeaderIgnoreCase(final Map<String, String> headers, final String name)
	{
		if (headers == null)
		{
			return null;
		}
		for (final Map.Entry<String, String> entry : headers.entrySet())
		{
			if (name.equalsIgnoreCase(entry.getKey()))
			{
				return entry.getValue();
			}
		}
		return null;
	}

	private static boolean constantTimeEquals(final String a, final String b)
	{
		return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * Defensive R2 guard (the core validator already checks this pre-activation): the token must be
	 * charged by a Chargebee gateway bound to the same Adyen merchant account it was minted under.
	 */
	protected void verifyMerchantAccount(final AdyenTokenHandle token) throws PreconditionFailedException
	{
		final String configured = configService.getConfiguredAdyenMerchantAccount();
		// Chargebee is an external gateway: a blank merchant account is a misconfiguration, not an
		// exemption. Fail closed so R2 cannot be silently bypassed (the core validator skips on null).
		if (StringUtils.isBlank(configured))
		{
			logTokenValidationFailure("merchant_account_not_configured", token, "configuration");
			LOG.error("event=merchant_account_mismatch platform=CHARGEBEE operation=import_token outcome=failure "
					+ "error_class=configuration correlation_id={} configured_merchant_account=missing "
					+ "token_merchant_account={}", correlationId(), token == null ? null : token.merchantAccount());
			throw new PreconditionFailedException("Chargebee connector has no configured Adyen merchant account "
					+ "(chargebee.adyenMerchantAccount); refusing to import a token without the R2 guarantee");
		}
		if (!configured.equals(token.merchantAccount()))
		{
			logTokenValidationFailure("merchant_account_mismatch", token, "validation");
			LOG.error("event=merchant_account_mismatch platform=CHARGEBEE operation=import_token outcome=failure "
					+ "error_class=validation correlation_id={} configured_merchant_account={} "
					+ "token_merchant_account={}", correlationId(), configured, token.merchantAccount());
			throw new PreconditionFailedException("Chargebee connector is bound to Adyen merchant account '" + configured
					+ "' but the token was minted under '" + token.merchantAccount() + "'");
		}
	}

	/**
	 * Build the Chargebee {@code reference_id}: {@code shopperReference/recurringDetailReference}
	 * (shopper first, slash-joined; {@code storedPaymentMethodId == recurringDetailReference}).
	 */
	protected String buildReferenceId(final AdyenTokenHandle token)
	{
		return token.shopperReference() + "/" + token.storedPaymentMethodId();
	}

	protected String itemPriceId(final PlanRef plan)
	{
		// resolvePlan maps the sendable Chargebee item price id into PlanRef.planId (priceId is the
		// optional separate price id, not what subscription_items[item_price_id] expects).
		return plan.planId();
	}

	public void setApiClient(final ChargebeeApiClient apiClient)
	{
		this.apiClient = apiClient;
	}

	public void setConfigService(final ChargebeeConfigService configService)
	{
		this.configService = configService;
	}

	public void setPlanResolver(final ChargebeePlanResolver planResolver)
	{
		this.planResolver = planResolver;
	}

	private void logTokenValidationFailure(final String reason, final AdyenTokenHandle token,
			final String errorClass)
	{
		LOG.warn("event=token_import_validation_failure platform=CHARGEBEE operation=import_token outcome=failure "
				+ "error_class={} reason={} correlation_id={} token_reference={} merchant_account={}", errorClass, reason,
				correlationId(), token == null ? null : token.storedPaymentMethodId(),
				token == null ? null : token.merchantAccount());
	}

	private static void logWebhookFailure(final long startedAt, final String reason, final String eventId,
			final int payloadBytes)
	{
		LOG.warn("event=webhook_processing platform=CHARGEBEE operation=parse_webhook outcome=failure duration_ms={} "
				+ "error_class=validation reason={} correlation_id={} event_id={} payload_bytes={} auth_verified=false",
				elapsedMillis(startedAt), reason, correlationId(), eventId, payloadBytes);
	}

	private static String webhookAuthFailureReason(final BillingException error)
	{
		final String message = StringUtils.defaultString(error.getMessage());
		if (message.contains("not configured")) return "webhook_auth_not_configured";
		if (message.contains("Base64")) return "authorization_header_invalid_base64";
		if (message.contains("do not match")) return "webhook_credentials_mismatch";
		return "authorization_header_missing_or_invalid";
	}

	private static String errorClass(final BillingException error)
	{
		final String type = error.getClass().getSimpleName();
		if (type.contains("Retryable")) return "remote_retryable";
		if (type.contains("Precondition")) return "validation";
		return "remote_terminal";
	}

	private static long elapsedMillis(final long startedAt)
	{
		return (System.nanoTime() - startedAt) / 1_000_000L;
	}

	private static String correlationId()
	{
		return StringUtils.defaultIfBlank(MDC.get("correlationId"), "none");
	}
}
