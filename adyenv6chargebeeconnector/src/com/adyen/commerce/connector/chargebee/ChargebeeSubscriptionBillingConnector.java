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
import java.util.Set;

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
import com.adyen.commerce.connector.dto.PaymentMethodChangeOutcome;
import com.adyen.commerce.connector.dto.PaymentMethodChangeRequest;
import com.adyen.commerce.connector.dto.PaymentMethodChangeScope;
import com.adyen.commerce.connector.dto.PaymentMethodChangeSupport;
import com.adyen.commerce.connector.dto.PaymentMethodEnrollmentSupport;
import com.adyen.commerce.connector.dto.PaymentMethodChoice;
import com.adyen.commerce.connector.dto.PaymentMethodSource;
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
import com.adyen.commerce.connector.exception.CapabilityUnsupportedException;
import com.adyen.commerce.connector.exception.PreconditionFailedException;
import com.adyen.commerce.connector.exception.TerminalBillingException;
import com.adyen.commerce.connector.log.ConnectorLogContext;
import com.adyen.commerce.connector.log.ConnectorLogEvent;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Chargebee adapter of the {@link SubscriptionBillingConnector} SPI: Adyen processes the recurring
 * payments, Chargebee only orchestrates the billing. Pause is not supported
 * ({@code supportsPause=false}), so the SPI default rejects it.
 */
public class ChargebeeSubscriptionBillingConnector implements SubscriptionBillingConnector
{
	private static final Logger LOG = LoggerFactory.getLogger(ChargebeeSubscriptionBillingConnector.class);

	private static final String EVENT_CONNECTOR_OPERATION = "connector_operation";
	private static final String EVENT_TOKEN_IMPORT_VALIDATION_FAILURE = "token_import_validation_failure";
	private static final String EVENT_WEBHOOK_PROCESSING = "webhook_processing";
	private static final String EVENT_RECONCILIATION_GAP = "reconciliation_gap";

	private static final String CUSTOMER_ID = "customer_id";
	private static final String SUBSCRIPTION_ID = "subscription_id";
	private static final String PLAN_ID = "plan_id";
	private static final String QUANTITY = "quantity";
	private static final String TOKEN_REFERENCE = "token_reference";
	private static final String MERCHANT_ACCOUNT = "merchant_account";
	private static final String PAYMENT_SOURCE_ID = "payment_source_id";
	private static final String ERROR_CLASS = "error_class";
	private static final String EVENT_ID = "event_id";
	private static final String VENDOR_EVENT_TYPE = "vendor_event_type";
	private static final String PAYLOAD_CHARS = "payload_chars";
	private static final String AUTH_VERIFIED = "auth_verified";
	private static final String OP_PARSE_WEBHOOK = "parse_webhook";

	private static final ConnectorCapabilities CAPABILITIES = new ConnectorCapabilities(
			false, // requiresNetworkTransactionId — Chargebee's token import does not need one
			true,  // supportsImmediateStart — subscription_for_items can start immediately
			false, // supportsPause — the SPI default rejects pause
			true,  // requiresPreConfiguredPlan — the item price must already exist in the Chargebee catalog
			true,  // liveTokenValidationOnImport — create_using_permanent_token makes a live retrieval call to Adyen
			TokenImportStyle.SLASH_JOINED, // reference_id = shopperReference/recurringDetailReference
			new PaymentMethodChangeSupport(PaymentMethodChangeScope.CUSTOMER,
					// Only a vaulted Adyen card: this adapter never reads Chargebee's own payment sources,
					// so declaring that source would offer options every submission refuses.
					Set.of(PaymentMethodSource.ADYEN_VAULTED_TOKEN)),
			// Chargebee hosts such a page, but this adapter does not request one; offering it would be a
			// link to nothing.
			PaymentMethodEnrollmentSupport.NONE);

	private static final String AUTHORIZATION_HEADER = "Authorization";
	private static final String BASIC_PREFIX = "Basic ";

	private final ObjectMapper objectMapper = new ObjectMapper();

	/** Injectable so a test controls the observed webhook lag; defaults to the system clock. */
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
		try (ConnectorLogContext ignored = ConnectorLogContext.open(platform(), "ensure_customer"))
		{
			final String customerId;
			try
			{
				customerId = apiClient.ensureCustomer(request.customerId(), request.email(), request.firstName(),
						request.lastName());
			}
			catch (final BillingException e)
			{
				// The requested id: the call that would have returned one is the call that failed.
				ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
						.failure(startedAt, e)
						.field(CUSTOMER_ID, request.customerId())
						.warn(LOG);
				throw e;
			}
			ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
					.success(startedAt)
					.field(CUSTOMER_ID, customerId)
					.info(LOG);
			return new BillingCustomerRef(BillingPlatform.CHARGEBEE, customerId);
		}
	}

	@Override
	public BillingPaymentMethodRef importAdyenToken(final TokenImportRequest request) throws BillingException
	{
		final long startedAt = System.nanoTime();
		try (ConnectorLogContext ignored = ConnectorLogContext.open(platform(), "import_token"))
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
						.field(TOKEN_REFERENCE, token.storedPaymentMethodId())
						.field(MERCHANT_ACCOUNT, token.merchantAccount())
						.warn(LOG);
				throw e;
			}
			ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
					.success(startedAt)
					.field(TOKEN_REFERENCE, token.storedPaymentMethodId())
					.field(PAYMENT_SOURCE_ID, paymentSourceId)
					.field(MERCHANT_ACCOUNT, token.merchantAccount())
					.info(LOG);
			return new BillingPaymentMethodRef(BillingPlatform.CHARGEBEE, paymentSourceId);
		}
	}

	@Override
	public PaymentMethodChangeOutcome changePaymentMethod(final PaymentMethodChangeRequest request)
			throws BillingException
	{
		final long startedAt = System.nanoTime();
		try (ConnectorLogContext ignored = ConnectorLogContext.open(platform(), "change_payment_method"))
		{
			// A switch expression, so another kind of choice is a compile error rather than a silent
			// fall-through.
			final AdyenTokenHandle token = switch (request.choice())
			{
				case PaymentMethodChoice.AdyenVaultedToken vaulted -> vaulted.token();
				case PaymentMethodChoice.AlreadyOnPlatform ignoredChoice -> throw new CapabilityUnsupportedException(
						"This Chargebee adapter changes a payment method by importing an Adyen-vaulted card; "
								+ "it does not repoint a subscription at a payment source Chargebee already holds");
			};
			verifyMerchantAccount(token);
			final String paymentSourceId;
			try
			{
				// The client sends replace_primary_payment_source, so creating the source and making it the
				// one billing uses are a single round trip. The derived reference id is deterministic, so
				// re-selecting the customer's current card is a no-op rather than a duplicate source.
				paymentSourceId = apiClient.importPermanentToken(request.customer().externalId(),
						buildReferenceId(token), token.cardMetadata());
			}
			catch (final BillingException e)
			{
				ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
						.failure(startedAt, e)
						.field(SUBSCRIPTION_ID, externalIdOrNull(request.subscription()))
						.field(TOKEN_REFERENCE, token.storedPaymentMethodId())
						.warn(LOG);
				throw e;
			}
			ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
					.success(startedAt)
					.field(SUBSCRIPTION_ID, externalIdOrNull(request.subscription()))
					.field(TOKEN_REFERENCE, token.storedPaymentMethodId())
					.field(PAYMENT_SOURCE_ID, paymentSourceId)
					.field("applied_scope", PaymentMethodChangeScope.CUSTOMER.name())
					.info(LOG);
			return new PaymentMethodChangeOutcome(
					new BillingPaymentMethodRef(BillingPlatform.CHARGEBEE, paymentSourceId),
					PaymentMethodChangeScope.CUSTOMER);
		}
	}

	@Override
	public PlanRef resolvePlan(final PlanResolutionRequest request) throws BillingException
	{
		final long startedAt = System.nanoTime();
		try (ConnectorLogContext ignored = ConnectorLogContext.open(platform(), "resolve_plan"))
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
						.field("base_store", request.baseStoreUid())
						.warn(LOG);
				throw e;
			}
			ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
					.success(startedAt)
					.field("product_code", request.productCode())
					.field("base_store", request.baseStoreUid())
					.field(PLAN_ID, itemPriceIdOrNull(plan))
					.info(LOG);
			return plan;
		}
	}

	@Override
	public BillingSubscriptionRef createSubscription(final SubscriptionCreateRequest request) throws BillingException
	{
		final long startedAt = System.nanoTime();
		try (ConnectorLogContext ignored = ConnectorLogContext.open(platform(), "create_subscription"))
		{
			try
			{
				return createSubscriptionInternal(request, startedAt);
			}
			catch (final BillingException e)
			{
				// Null-tolerant accessors: a failure raised before the request was fully built leaves these
				// unset, and an NPE from the logging would replace the cause.
				ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
						.failure(startedAt, e)
						.field(PLAN_ID, itemPriceIdOrNull(request.plan()))
						.field(PAYMENT_SOURCE_ID, externalIdOrNull(request.paymentMethod()))
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
				.field(SUBSCRIPTION_ID, subscriptionId)
				.field(PLAN_ID, itemPriceId(request.plan()))
				.field(QUANTITY, request.quantity())
				.field("start_epoch_seconds", startEpochSeconds)
				.field(PAYMENT_SOURCE_ID, request.paymentMethod().externalId())
				.info(LOG);
		return new BillingSubscriptionRef(BillingPlatform.CHARGEBEE, subscriptionId);
	}

	@Override
	public NormalizedSubscription fetchSubscription(final BillingSubscriptionRef subscription) throws BillingException
	{
		final long startedAt = System.nanoTime();
		try (ConnectorLogContext ignored = ConnectorLogContext.open(platform(), "fetch_subscription"))
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
						.field(SUBSCRIPTION_ID, subscription.externalId())
						.warn(LOG);
				throw e;
			}
			ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
					.success(startedAt)
					.field(SUBSCRIPTION_ID, subscription.externalId())
					.field("subscription_status", fetched == null ? null : fetched.status())
					.info(LOG);
			return fetched;
		}
	}

	@Override
	public void updateSubscription(final SubscriptionUpdateRequest request) throws BillingException
	{
		final long startedAt = System.nanoTime();
		try (ConnectorLogContext ignored = ConnectorLogContext.open(platform(), "update_subscription"))
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
						.field(SUBSCRIPTION_ID, externalIdOrNull(request.subscription()))
						.field(PLAN_ID, itemPriceId)
						.field(QUANTITY, request.quantity())
						.warn(LOG);
				throw e;
			}
			ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
					.success(startedAt)
					.field(SUBSCRIPTION_ID, request.subscription().externalId())
					.field(PLAN_ID, itemPriceId)
					.field(QUANTITY, request.quantity())
					.info(LOG);
		}
	}

	@Override
	public void cancelSubscription(final SubscriptionCancelRequest request) throws BillingException
	{
		final long startedAt = System.nanoTime();
		try (ConnectorLogContext ignored = ConnectorLogContext.open(platform(), "cancel_subscription"))
		{
			// A switch expression, so another timing is a compile error rather than a cancellation at
			// whichever moment the surviving branch happens to mean. Chargebee reaches the same endpoint
			// either way and differs only in cancel_option, so the choice can stay a flag here.
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
						.field(SUBSCRIPTION_ID, externalIdOrNull(request.subscription()))
						.field("cancellation_timing", ConnectorLogContext.code(request.timing()))
						.warn(LOG);
				throw e;
			}
			ConnectorLogEvent.of(EVENT_CONNECTOR_OPERATION)
					.success(startedAt)
					.field(SUBSCRIPTION_ID, request.subscription().externalId())
					.field("cancellation_timing", ConnectorLogContext.code(request.timing()))
					.info(LOG);
		}
	}

	// pauseSubscription is intentionally not overridden: supportsPause=false, so the SPI default
	// throws CapabilityUnsupportedException.

	@Override
	public NormalizedBillingEvent parseWebhook(final RawWebhook raw) throws BillingException
	{
		final long startedAt = System.nanoTime();
		try (ConnectorLogContext ignored = ConnectorLogContext.open(platform(), OP_PARSE_WEBHOOK))
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
			// Basic Auth passed: a malformed body must not land on the same alert as an unauthenticated one.
			logWebhookFailure(startedAt, "payload_parsing_failed", null, payloadChars, true);
			throw new TerminalBillingException("Chargebee webhook payload is not valid JSON", e);
		}

		final String chargebeeEventType = root.path("event_type").asText(null);
		final BillingEventType type = mapEventType(chargebeeEventType);
		if (type == null)
		{
			// Chargebee fires many event types this connector does not act on; acknowledge without
			// erroring, since the dispatcher no-ops on a null event.
			webhookEvent()
					.outcome(ConnectorLogEvent.OUTCOME_IGNORED)
					.durationSince(startedAt)
					.field(ERROR_CLASS, ConnectorLogEvent.ERROR_CLASS_NONE)
					.reason("unsupported_event_type")
					.field(EVENT_ID, root.path("id").asText(null))
					.field(VENDOR_EVENT_TYPE, chargebeeEventType)
					.field(PAYLOAD_CHARS, payloadChars)
					.field(AUTH_VERIFIED, Boolean.TRUE)
					.info(LOG);
			return null;
		}

		final JsonNode content = root.path("content");
		final String externalSubscriptionId = firstNonBlank(
				content.path("subscription").path("id").asText(null),
				content.path("transaction").path(SUBSCRIPTION_ID).asText(null),
				content.path("invoice").path(SUBSCRIPTION_ID).asText(null));
		final String externalCustomerId = firstNonBlank(
				content.path("customer").path("id").asText(null),
				content.path("subscription").path(CUSTOMER_ID).asText(null),
				content.path("transaction").path(CUSTOMER_ID).asText(null),
				content.path("invoice").path(CUSTOMER_ID).asText(null));

		final long occurredAtEpochSeconds = root.path("occurred_at").asLong(0L);
		final Instant occurredAt = occurredAtEpochSeconds > 0
				? Instant.ofEpochSecond(occurredAtEpochSeconds)
				: clock.instant();

		// Chargebee documents the event id as the deduplication key, and the core dedups on it.
		final String eventId = root.path("id").asText(null);
		// Negative when Chargebee's clock is ahead; the sign is the skew signal.
		final long lagMs = clock.instant().toEpochMilli() - occurredAt.toEpochMilli();
		webhookEvent()
				.success(startedAt)
				.field(EVENT_ID, eventId)
				.field(SUBSCRIPTION_ID, externalSubscriptionId)
				.field(VENDOR_EVENT_TYPE, chargebeeEventType)
				.field("normalized_event_type", type)
				.field("webhook_lag_ms", lagMs)
				.field(PAYLOAD_CHARS, payloadChars)
				.field(AUTH_VERIFIED, Boolean.TRUE)
				.info(LOG);
		if (StringUtils.isBlank(externalSubscriptionId))
		{
			ConnectorLogEvent.of(EVENT_RECONCILIATION_GAP)
					.platform(BillingPlatform.CHARGEBEE)
					.operation(OP_PARSE_WEBHOOK)
					.outcome(ConnectorLogEvent.OUTCOME_UNRESOLVED)
					.durationSince(startedAt)
					.field(ERROR_CLASS, ConnectorLogEvent.ERROR_CLASS_NONE)
					.reason("subscription_id_missing")
					.field(EVENT_ID, eventId)
					.field(VENDOR_EVENT_TYPE, chargebeeEventType)
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
	 * Maps a Chargebee {@code event_type} to the normalized vocabulary, returning {@code null} for the
	 * many types this connector does not act on (see {@link #parseWebhook}). An event is mapped only when
	 * it announces a change to state the local projection holds — status, plan, quantity, period,
	 * {@code cancelAtPeriodEnd} — which is what keeps reminder, invoice, payment-source and
	 * scheduled-plan-change events out; a renewal is already covered by {@code payment_succeeded}.
	 * Chargebee announces a backdated operation under a separate event type and does not also send the
	 * plain one, so both spellings are mapped — and it spells the cancellation variant with one
	 * {@code l} where the base event has two.
	 */
	protected BillingEventType mapEventType(final String chargebeeEventType)
	{
		if (chargebeeEventType == null)
		{
			return null;
		}
		return switch (chargebeeEventType)
		{
			// A subscription booked with a start date announces subscription_started; subscription_activated
			// marks a trial ending. Both are mapped so neither configuration has a blind spot.
			case "subscription_started", "subscription_activated", "subscription_activated_with_backdating",
					"subscription_reactivated", "subscription_reactivated_with_backdating"
					-> BillingEventType.SUBSCRIPTION_ACTIVATED;
			case "subscription_changed", "subscription_changed_with_backdating"
					-> BillingEventType.SUBSCRIPTION_UPDATED;
			// The hosted portal's cancel button; without it the local projection keeps promising a renewal
			// Chargebee has already been told not to make.
			case "subscription_cancellation_scheduled" -> BillingEventType.SUBSCRIPTION_CANCELLATION_SCHEDULED;
			case "subscription_scheduled_cancellation_removed" -> BillingEventType.SUBSCRIPTION_CANCELLATION_REMOVED;
			case "subscription_cancelled", "subscription_canceled_with_backdating"
					-> BillingEventType.SUBSCRIPTION_CANCELLED;
			// Pause is refused outbound (supportsPause=false), but an operator can still pause in
			// Chargebee's own panel, and the normalized status vocabulary has a word for the result.
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
		// exemption, so fail closed rather than let the check be bypassed.
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
	 * Refuses a reference belonging to another platform. Chargebee subscription ids are caller-chosen, so
	 * sending a foreign id could hit an unrelated subscription rather than merely 404.
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
	 * Builds the Chargebee {@code reference_id}: {@code shopperReference/recurringDetailReference}
	 * ({@code storedPaymentMethodId == recurringDetailReference}).
	 */
	protected String buildReferenceId(final AdyenTokenHandle token)
	{
		return token.shopperReference() + "/" + token.storedPaymentMethodId();
	}

	protected String itemPriceId(final PlanRef plan)
	{
		// resolvePlan puts the sendable Chargebee item price id in PlanRef.planId; priceId is a separate
		// optional id, not what subscription_items[item_price_id] expects.
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
	 * One event for every refused token import, told apart by {@code reason}. Platform and operation are
	 * stated explicitly because this guard is {@code protected} and can be called outside the scope
	 * {@link #importAdyenToken} opens; when that scope is open, its values win.
	 */
	private ConnectorLogEvent tokenValidationFailure(final String reason, final String errorClass,
			final AdyenTokenHandle token)
	{
		return ConnectorLogEvent.of(EVENT_TOKEN_IMPORT_VALIDATION_FAILURE)
				.platform(BillingPlatform.CHARGEBEE)
				.operation("import_token")
				.outcome(ConnectorLogEvent.OUTCOME_FAILURE)
				.field(ERROR_CLASS, errorClass)
				.reason(reason)
				.field(TOKEN_REFERENCE, token == null ? null : token.storedPaymentMethodId())
				.field(MERCHANT_ACCOUNT, token == null ? null : token.merchantAccount());
	}

	private void logWebhookFailure(final long startedAt, final String reason, final String eventId,
			final int payloadChars, final boolean authVerified)
	{
		webhookEvent()
				.outcome(ConnectorLogEvent.OUTCOME_FAILURE)
				.durationSince(startedAt)
				.field(ERROR_CLASS, ConnectorLogEvent.ERROR_CLASS_VALIDATION)
				.reason(reason)
				.field(EVENT_ID, eventId)
				.field(PAYLOAD_CHARS, payloadChars)
				.field(AUTH_VERIFIED, authVerified)
				.warn(LOG);
	}

	private ConnectorLogEvent webhookEvent()
	{
		return ConnectorLogEvent.of(EVENT_WEBHOOK_PROCESSING)
				.platform(BillingPlatform.CHARGEBEE)
				.operation(OP_PARSE_WEBHOOK);
	}

	/**
	 * The reason travels on the exception rather than being matched out of its message text. Missing
	 * configuration is the one case that arrives as a plain {@link PreconditionFailedException}, being a
	 * local misconfiguration rather than a bad request from Chargebee.
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
