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
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import com.adyen.commerce.connector.dto.NormalizedSubscription;
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
import com.adyen.commerce.connector.log.ConnectorLogContext;
import com.adyen.commerce.connector.log.ConnectorLogEvent;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Chargebee adapter of the {@link SubscriptionBillingConnector} SPI: Adyen keeps processing the
 * recurring payments, Chargebee only orchestrates the billing.
 *
 * <p>This first cut covers the outbound lifecycle (customer, token import, plan resolution,
 * subscription create/update/cancel), the authoritative read used by reconciliation, plus inbound
 * webhook verification/normalization.
 * Pause is not supported ({@code supportsPause=false}, SPI default rejects it).</p>
 */
public class ChargebeeSubscriptionBillingConnector implements SubscriptionBillingConnector
{
	private static final Logger LOG = LoggerFactory.getLogger(ChargebeeSubscriptionBillingConnector.class);

	private static final String EVENT_CONNECTOR_OPERATION = "connector_operation";
	private static final String EVENT_TOKEN_IMPORT_VALIDATION_FAILURE = "token_import_validation_failure";
	private static final String EVENT_WEBHOOK_PROCESSING = "webhook_processing";
	private static final String EVENT_RECONCILIATION_GAP = "reconciliation_gap";

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

	/**
	 * Injectable so the webhook lag a test observes is the one it set up. Defaults to the system clock,
	 * which keeps the Spring definition free of a second wiring line.
	 */
	private Clock clock = Clock.systemUTC();

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
		try (ConnectorLogContext scope = ConnectorLogContext.open(platform(), "ensure_customer"))
		{
			final String customerId;
			try
			{
				customerId = apiClient.ensureCustomer(request.customerId(), request.email(), request.firstName(),
						request.lastName());
			}
			catch (final BillingException e)
			{
				// The requested id, not the returned one: the call that would have returned it is the
				// one that just failed, so the field would have been empty on every failure line.
				ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
						.failure(startedAt, e)
						.field("customer_id", request.customerId())
						.warn(LOG);
				throw e;
			}
			ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
					.success(startedAt)
					.field("customer_id", customerId)
					.info(LOG);
			return new BillingCustomerRef(BillingPlatform.CHARGEBEE, customerId);
		}
	}

	@Override
	public BillingPaymentMethodRef importAdyenToken(final TokenImportRequest request) throws BillingException
	{
		final long startedAt = System.nanoTime();
		try (ConnectorLogContext scope = ConnectorLogContext.open(platform(), "import_token"))
		{
			final AdyenTokenHandle token = request.token();
			verifyMerchantAccount(token);
			final String paymentSourceId;
			try
			{
				paymentSourceId = apiClient.importPermanentToken(request.customer().externalId(),
						buildReferenceId(token), token.cardMetadata());
			}
			catch (final BillingException e)
			{
				ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
						.failure(startedAt, e)
						.field("token_reference", token.storedPaymentMethodId())
						.field("merchant_account", token.merchantAccount())
						.warn(LOG);
				throw e;
			}
			ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
					.success(startedAt)
					.field("token_reference", token.storedPaymentMethodId())
					.field("payment_source_id", paymentSourceId)
					.field("merchant_account", token.merchantAccount())
					.info(LOG);
			return new BillingPaymentMethodRef(BillingPlatform.CHARGEBEE, paymentSourceId);
		}
	}

	@Override
	public PlanRef resolvePlan(final PlanResolutionRequest request) throws BillingException
	{
		final long startedAt = System.nanoTime();
		try (ConnectorLogContext scope = ConnectorLogContext.open(platform(), "resolve_plan"))
		{
			final PlanRef plan;
			try
			{
				plan = planResolver.resolve(request);
			}
			catch (final BillingException e)
			{
				ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
						.failure(startedAt, e)
						.field("product_code", request.productCode())
						.warn(LOG);
				throw e;
			}
			ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
					.success(startedAt)
					.field("product_code", request.productCode())
					.field("plan_id", itemPriceIdOrNull(plan))
					.info(LOG);
			return plan;
		}
	}

	@Override
	public BillingSubscriptionRef createSubscription(final SubscriptionCreateRequest request) throws BillingException
	{
		final long startedAt = System.nanoTime();
		try (ConnectorLogContext scope = ConnectorLogContext.open(platform(), "create_subscription"))
		{
			try
			{
				return createSubscriptionInternal(request, startedAt);
			}
			catch (final BillingException e)
			{
				// Null-tolerant accessors: a failure raised before the request was fully built is
				// exactly when these are unset, and an NPE from the logging would replace the cause.
				ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
						.failure(startedAt, e)
						.field("plan_id", itemPriceIdOrNull(request.plan()))
						.field("payment_source_id", externalIdOrNull(request.paymentMethod()))
						.warn(LOG);
				throw e;
			}
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
		ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
				.success(startedAt)
				.field("subscription_id", subscriptionId)
				.field("plan_id", itemPriceId(request.plan()))
				.field("quantity", Integer.valueOf(request.quantity()))
				.field("start_epoch_seconds", startEpochSeconds)
				.field("payment_source_id", request.paymentMethod().externalId())
				.info(LOG);
		return new BillingSubscriptionRef(BillingPlatform.CHARGEBEE, subscriptionId);
	}

	@Override
	public NormalizedSubscription fetchSubscription(final BillingSubscriptionRef subscription) throws BillingException
	{
		final long startedAt = System.nanoTime();
		try (ConnectorLogContext scope = ConnectorLogContext.open(platform(), "fetch_subscription"))
		{
			verifyChargebeeSubscription(subscription);
			final NormalizedSubscription fetched;
			try
			{
				fetched = apiClient.fetchSubscription(subscription.externalId());
			}
			catch (final BillingException e)
			{
				ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
						.failure(startedAt, e)
						.field("subscription_id", subscription.externalId())
						.warn(LOG);
				throw e;
			}
			ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
					.success(startedAt)
					.field("subscription_id", subscription.externalId())
					.field("subscription_status", fetched == null ? null : fetched.status())
					.info(LOG);
			return fetched;
		}
	}

	@Override
	public void updateSubscription(final SubscriptionUpdateRequest request) throws BillingException
	{
		final long startedAt = System.nanoTime();
		try (ConnectorLogContext scope = ConnectorLogContext.open(platform(), "update_subscription"))
		{
			final String itemPriceId = request.plan() == null ? null : itemPriceId(request.plan());
			try
			{
				apiClient.updateSubscription(request.subscription().externalId(), itemPriceId, request.quantity());
			}
			catch (final BillingException e)
			{
				ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
						.failure(startedAt, e)
						.field("subscription_id", externalIdOrNull(request.subscription()))
						.field("plan_id", itemPriceId)
						.field("quantity", request.quantity())
						.warn(LOG);
				throw e;
			}
			ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
					.success(startedAt)
					.field("subscription_id", request.subscription().externalId())
					.field("plan_id", itemPriceId)
					.field("quantity", request.quantity())
					.info(LOG);
		}
	}

	@Override
	public void cancelSubscription(final SubscriptionCancelRequest request) throws BillingException
	{
		final long startedAt = System.nanoTime();
		try (ConnectorLogContext scope = ConnectorLogContext.open(platform(), "cancel_subscription"))
		{
			// A switch expression, not a statement: only the expression form is checked for exhaustiveness,
			// so this is what makes a third timing a compile error instead of one that quietly cancels a
			// subscription at whichever moment the surviving branch happens to mean. Chargebee reaches the
			// same endpoint either way and differs only in cancel_option, which is why the choice can stay a
			// flag here where on Recurly it could not.
			final boolean atPeriodEnd = switch (request.timing())
			{
				case AT_PERIOD_END -> true;
				case IMMEDIATELY -> false;
			};
			try
			{
				apiClient.cancelSubscription(request.subscription().externalId(), atPeriodEnd);
			}
			catch (final BillingException e)
			{
				ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
						.failure(startedAt, e)
						.field("subscription_id", externalIdOrNull(request.subscription()))
						.field("cancellation_timing", ConnectorLogContext.code(request.timing()))
						.warn(LOG);
				throw e;
			}
			ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
					.success(startedAt)
					.field("subscription_id", request.subscription().externalId())
					.field("cancellation_timing", ConnectorLogContext.code(request.timing()))
					.info(LOG);
		}
	}

	// pauseSubscription is intentionally NOT overridden: supportsPause=false, so the SPI default
	// throws CapabilityUnsupportedException. Chargebee pause/resume will be added in a later increment.

	@Override
	public NormalizedBillingEvent parseWebhook(final RawWebhook raw) throws BillingException
	{
		final long startedAt = System.nanoTime();
		try (ConnectorLogContext scope = ConnectorLogContext.open(platform(), "parse_webhook"))
		{
			return parseWebhookInternal(raw, startedAt);
		}
	}

	private NormalizedBillingEvent parseWebhookInternal(final RawWebhook raw, final long startedAt)
			throws BillingException
	{
		if (raw == null)
		{
			logWebhookFailure(startedAt, "webhook_missing", null, 0, false);
			throw new TerminalBillingException("Chargebee webhook is missing");
		}
		final int payloadChars = raw.payload() == null ? 0 : raw.payload().length();
		if (StringUtils.isBlank(raw.payload()))
		{
			logWebhookFailure(startedAt, "payload_missing", null, payloadChars, false);
			throw new TerminalBillingException("Chargebee webhook payload is missing");
		}
		try
		{
			verifyWebhookAuth(raw);
		}
		catch (final BillingException e)
		{
			logWebhookFailure(startedAt, webhookAuthFailureReason(e), null, payloadChars, false);
			throw e;
		}

		final JsonNode root;
		try
		{
			root = objectMapper.readTree(raw.payload());
		}
		catch (final IOException e)
		{
			// Basic Auth did pass; reporting otherwise would put a malformed body on the same alert as
			// an unauthenticated one.
			logWebhookFailure(startedAt, "payload_parsing_failed", null, payloadChars, true);
			throw new TerminalBillingException("Chargebee webhook payload is not valid JSON", e);
		}

		final String chargebeeEventType = root.path("event_type").asText(null);
		final BillingEventType type = mapEventType(chargebeeEventType);
		if (type == null)
		{
			// Chargebee fires many event types we don't act on (invoice_generated, customer_changed, ...).
			// Acknowledge without erroring: the dispatcher no-ops on a null event.
			webhookEvent()
					.outcome(ConnectorLogEvent.OUTCOME_IGNORED)
					.durationSince(startedAt)
					.field("error_class", ConnectorLogEvent.ERROR_CLASS_NONE)
					.reason("unsupported_event_type")
					.field("event_id", root.path("id").asText(null))
					.field("vendor_event_type", chargebeeEventType)
					.field("payload_chars", Integer.valueOf(payloadChars))
					.field("auth_verified", Boolean.TRUE)
					.info(LOG);
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
		final Instant occurredAt = occurredAtEpochSeconds > 0
				? Instant.ofEpochSecond(occurredAtEpochSeconds)
				: clock.instant();

		// Chargebee's own docs recommend deduplicating on the event id; the core does exactly that, so it
		// travels as a first-class field rather than an attribute.
		final String eventId = root.path("id").asText(null);
		// Negative when Chargebee's clock is ahead of ours; the sign is the skew signal, so it travels as
		// the value rather than as a second derived flag.
		final long lagMs = clock.instant().toEpochMilli() - occurredAt.toEpochMilli();
		webhookEvent()
				.success(startedAt)
				.field("event_id", eventId)
				.field("subscription_id", externalSubscriptionId)
				.field("vendor_event_type", chargebeeEventType)
				.field("normalized_event_type", type)
				.field("webhook_lag_ms", Long.valueOf(lagMs))
				.field("payload_chars", Integer.valueOf(payloadChars))
				.field("auth_verified", Boolean.TRUE)
				.info(LOG);
		if (StringUtils.isBlank(externalSubscriptionId))
		{
			ConnectorLogEvent.of(EVENT_RECONCILIATION_GAP)
					.platform(BillingPlatform.CHARGEBEE)
					.operation("parse_webhook")
					.outcome(ConnectorLogEvent.OUTCOME_UNRESOLVED)
					.durationSince(startedAt)
					.field("error_class", ConnectorLogEvent.ERROR_CLASS_NONE)
					.reason("subscription_id_missing")
					.field("event_id", eventId)
					.field("vendor_event_type", chargebeeEventType)
					.warn(LOG);
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
					+ "(Chargebee Config: Webhook Username/Webhook Password) are not configured on the base store");
		}

		final String authorizationHeader = findHeaderIgnoreCase(raw.headers(), AUTHORIZATION_HEADER);
		if (StringUtils.isBlank(authorizationHeader) || !authorizationHeader.startsWith(BASIC_PREFIX))
		{
			throw new WebhookAuthException("authorization_header_missing_or_invalid",
					"Chargebee webhook is missing a valid Basic Authorization header");
		}

		final String decoded;
		try
		{
			decoded = new String(Base64.getDecoder().decode(authorizationHeader.substring(BASIC_PREFIX.length())),
					StandardCharsets.UTF_8);
		}
		catch (final IllegalArgumentException e)
		{
			throw new WebhookAuthException("authorization_header_invalid_base64",
					"Chargebee webhook Authorization header is not valid Base64", e);
		}

		final int colonIndex = decoded.indexOf(':');
		final String actualUsername = colonIndex >= 0 ? decoded.substring(0, colonIndex) : decoded;
		final String actualPassword = colonIndex >= 0 ? decoded.substring(colonIndex + 1) : "";

		if (!constantTimeEquals(expectedUsername, actualUsername) || !constantTimeEquals(expectedPassword, actualPassword))
		{
			throw new WebhookAuthException("webhook_credentials_mismatch",
					"Chargebee webhook Basic Auth credentials do not match");
		}
	}

	/**
	 * Maps a Chargebee {@code event_type} to the normalized vocabulary. Unrecognized types return
	 * {@code null} (see {@link #parseWebhook}) rather than throwing, since Chargebee sends many event
	 * types this connector doesn't act on.
	 *
	 * <h3>What is mapped, and what is left alone</h3>
	 * <p>An event earns a place here only if the state it announces is one the local projection actually
	 * holds — status, plan, quantity, period, {@code cancelAtPeriodEnd} — and this is the moment that state
	 * changes. Everything else costs a live subscription read per delivery to write back the values that
	 * were already there. That rule is what keeps reminders, invoice and payment-source events, and the
	 * scheduled-plan-change family out: none of them has anywhere to land.</p>
	 *
	 * <p>Renewal is the instructive omission. It looks like the most obviously useful event of all, and it
	 * is already covered: a renewal charges the card, and {@code payment_succeeded} carries the
	 * subscription id and is mapped below. Mapping the renewal event as well would reconcile the same
	 * moment twice, and — because the renewal maps onto a subscription-scoped type while the payment does
	 * not — it would do so through the path that answers with an error and asks for a redelivery when the
	 * subscription is not one of ours. Every subscription on the site that this store did not create, once
	 * per billing cycle, forever.</p>
	 *
	 * <h3>Two spellings for one event</h3>
	 * <p>Chargebee announces a backdated operation under a separate event type and does not also send the
	 * plain one, so an unmapped variant is a silent gap in an otherwise mapped lifecycle rather than a
	 * missing extra. They are listed alongside their base events; a site with backdating switched off
	 * simply never sends them. Note that Chargebee spells the cancellation variant with one {@code l}
	 * where the base event has two — its inconsistency, not a typo here.</p>
	 */
	protected BillingEventType mapEventType(final String chargebeeEventType)
	{
		if (chargebeeEventType == null)
		{
			return null;
		}
		return switch (chargebeeEventType)
		{
			// subscription_started, not subscription_activated, is what a subscription this connector
			// created actually announces: it is booked with a start date and Chargebee's own scheduler
			// begins it, whereas subscription_activated marks a trial ending — which this integration
			// never sets up. Both are mapped so neither configuration has a blind spot.
			case "subscription_started", "subscription_activated", "subscription_activated_with_backdating",
					"subscription_reactivated", "subscription_reactivated_with_backdating"
					-> BillingEventType.SUBSCRIPTION_ACTIVATED;
			case "subscription_changed", "subscription_changed_with_backdating"
					-> BillingEventType.SUBSCRIPTION_UPDATED;
			// The hosted portal's cancel button. Without this the local projection keeps promising a
			// renewal that Chargebee has already been told not to make.
			case "subscription_cancellation_scheduled" -> BillingEventType.SUBSCRIPTION_CANCELLATION_SCHEDULED;
			case "subscription_scheduled_cancellation_removed" -> BillingEventType.SUBSCRIPTION_CANCELLATION_REMOVED;
			case "subscription_cancelled", "subscription_canceled_with_backdating"
					-> BillingEventType.SUBSCRIPTION_CANCELLED;
			// Pausing is refused on the way out (supportsPause=false), which says nothing about the way in:
			// an operator can pause in Chargebee's own panel, and the normalized status vocabulary already
			// has a word for the result.
			case "subscription_paused" -> BillingEventType.SUBSCRIPTION_PAUSED;
			case "subscription_resumed" -> BillingEventType.SUBSCRIPTION_RESUMED;
			case "payment_succeeded" -> BillingEventType.INVOICE_PAID;
			case "payment_failed" -> BillingEventType.INVOICE_PAYMENT_FAILED;
			default -> null;
		};
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
	 * Defensive gateway-binding guard (the core validator already checks this pre-activation): the token must be
	 * charged by a Chargebee gateway bound to the same Adyen merchant account it was minted under.
	 */
	protected void verifyMerchantAccount(final AdyenTokenHandle token) throws PreconditionFailedException
	{
		final String configured = configService.getConfiguredAdyenMerchantAccount();
		// Chargebee is an external gateway: a blank merchant account is a misconfiguration, not an
		// exemption. Fail closed so the check cannot be silently bypassed (the core validator skips on null).
		if (StringUtils.isBlank(configured))
		{
			tokenValidationFailure("merchant_account_not_configured", ConnectorLogEvent.ERROR_CLASS_CONFIGURATION,
					token).error(LOG);
			throw new PreconditionFailedException("Chargebee connector has no configured Adyen merchant account "
					+ "(Chargebee Config: Adyen Gateway Merchant Account); refusing to import a token "
					+ "without that guarantee");
		}
		if (!configured.equals(token.merchantAccount()))
		{
			tokenValidationFailure("merchant_account_mismatch", ConnectorLogEvent.ERROR_CLASS_VALIDATION, token)
					.field("configured_merchant_account", configured)
					.error(LOG);
			throw new PreconditionFailedException("Chargebee connector is bound to Adyen merchant account '" + configured
					+ "' but the token was minted under '" + token.merchantAccount() + "'");
		}
	}

	/**
	 * Reconciliation resolves the connector from the stored reference's own platform, so a mismatch here means
	 * the caller built the reference by hand. Refuse it instead of sending another platform's id to Chargebee,
	 * where it would either 404 or — worse, ids being caller-chosen here — hit an unrelated subscription.
	 */
	protected void verifyChargebeeSubscription(final BillingSubscriptionRef subscription)
			throws PreconditionFailedException
	{
		if (subscription == null)
		{
			throw new PreconditionFailedException("Cannot fetch a null subscription reference");
		}
		if (subscription.platform() != BillingPlatform.CHARGEBEE)
		{
			throw new PreconditionFailedException("Cannot fetch a " + subscription.platform()
					+ " subscription reference using the Chargebee connector");
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

	/**
	 * One event for every refused token import, told apart by {@code reason}. Kept to a single line so a
	 * count of the event is a count of the refusals rather than of how many times the same refusal was
	 * written down.
	 *
	 * <p>Platform and operation are stated explicitly because this guard is {@code protected} and can be
	 * called outside the scope {@link #importAdyenToken} opens; when that scope is open its values
	 * win.</p>
	 */
	private ConnectorLogEvent tokenValidationFailure(final String reason, final String errorClass,
			final AdyenTokenHandle token)
	{
		return ConnectorLogEvent.of(EVENT_TOKEN_IMPORT_VALIDATION_FAILURE)
				.platform(BillingPlatform.CHARGEBEE)
				.operation("import_token")
				.outcome(ConnectorLogEvent.OUTCOME_FAILURE)
				.field("error_class", errorClass)
				.reason(reason)
				.field("token_reference", token == null ? null : token.storedPaymentMethodId())
				.field("merchant_account", token == null ? null : token.merchantAccount());
	}

	private void logWebhookFailure(final long startedAt, final String reason, final String eventId,
			final int payloadChars, final boolean authVerified)
	{
		webhookEvent()
				.outcome(ConnectorLogEvent.OUTCOME_FAILURE)
				.durationSince(startedAt)
				.field("error_class", ConnectorLogEvent.ERROR_CLASS_VALIDATION)
				.reason(reason)
				.field("event_id", eventId)
				.field("payload_chars", Integer.valueOf(payloadChars))
				.field("auth_verified", Boolean.valueOf(authVerified))
				.warn(LOG);
	}

	private ConnectorLogEvent webhookEvent()
	{
		return ConnectorLogEvent.of(EVENT_WEBHOOK_PROCESSING)
				.platform(BillingPlatform.CHARGEBEE)
				.operation("parse_webhook");
	}

	/**
	 * The reason is carried by the exception rather than recovered from its wording: matching on
	 * {@code getMessage()} makes every reason label hostage to a copy edit. {@code not configured} is the
	 * one case that arrives as a plain {@link PreconditionFailedException}, since it is a misconfiguration
	 * on our side rather than a bad request from Chargebee.
	 */
	private static String webhookAuthFailureReason(final BillingException error)
	{
		if (error instanceof WebhookAuthException authFailure)
		{
			return authFailure.reason();
		}
		if (error instanceof PreconditionFailedException)
		{
			return "webhook_auth_not_configured";
		}
		return "authorization_header_missing_or_invalid";
	}

	private String itemPriceIdOrNull(final PlanRef plan)
	{
		return plan == null ? null : itemPriceId(plan);
	}

	private static String externalIdOrNull(final BillingPaymentMethodRef paymentMethod)
	{
		return paymentMethod == null ? null : paymentMethod.externalId();
	}

	private static String externalIdOrNull(final BillingSubscriptionRef subscription)
	{
		return subscription == null ? null : subscription.externalId();
	}

	void setClock(final Clock clock)
	{
		this.clock = clock;
	}

	/**
	 * A webhook authentication rejection that names its own reason. Still a
	 * {@link TerminalBillingException}, so nothing outside this class has to know it exists.
	 */
	protected static class WebhookAuthException extends TerminalBillingException
	{
		private static final long serialVersionUID = 1L;

		private final String reason;

		public WebhookAuthException(final String reason, final String message)
		{
			super(message);
			this.reason = reason;
		}

		public WebhookAuthException(final String reason, final String message, final Throwable cause)
		{
			super(message, cause);
			this.reason = reason;
		}

		public String reason()
		{
			return reason;
		}
	}
}
