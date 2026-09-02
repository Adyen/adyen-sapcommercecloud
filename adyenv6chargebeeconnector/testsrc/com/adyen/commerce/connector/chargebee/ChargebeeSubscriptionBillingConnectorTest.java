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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adyen.commerce.connector.chargebee.client.ChargebeeApiClient;
import com.adyen.commerce.connector.chargebee.client.ChargebeeSubscriptionParams;
import com.adyen.commerce.connector.chargebee.config.ChargebeeConfigService;
import com.adyen.commerce.connector.chargebee.plan.ChargebeePlanResolver;
import com.adyen.commerce.connector.dto.AdyenTokenHandle;
import com.adyen.commerce.connector.dto.BillingCustomerRef;
import com.adyen.commerce.connector.dto.BillingEventType;
import com.adyen.commerce.connector.dto.BillingPaymentMethodRef;
import com.adyen.commerce.connector.dto.BillingSubscriptionRef;
import com.adyen.commerce.connector.dto.CancelReason;
import com.adyen.commerce.connector.dto.CancellationTiming;
import com.adyen.commerce.connector.dto.ConnectorCapabilities;
import com.adyen.commerce.connector.dto.CustomerSyncRequest;
import com.adyen.commerce.connector.dto.NormalizedBillingEvent;
import com.adyen.commerce.connector.dto.NormalizedSubscription;
import com.adyen.commerce.connector.dto.NormalizedSubscriptionStatus;
import com.adyen.commerce.connector.dto.PlanRef;
import com.adyen.commerce.connector.dto.PlanResolutionRequest;
import com.adyen.commerce.connector.dto.RawWebhook;
import com.adyen.commerce.connector.dto.RecurringProcessingModel;
import com.adyen.commerce.connector.dto.SubscriptionCancelRequest;
import com.adyen.commerce.connector.dto.SubscriptionCreateRequest;
import com.adyen.commerce.connector.dto.SubscriptionPauseRequest;
import com.adyen.commerce.connector.dto.SubscriptionUpdateRequest;
import com.adyen.commerce.connector.dto.TokenImportRequest;
import com.adyen.commerce.connector.dto.TokenImportStyle;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.CapabilityUnsupportedException;
import com.adyen.commerce.connector.exception.PreconditionFailedException;
import com.adyen.commerce.connector.exception.TerminalBillingException;

import de.hybris.bootstrap.annotations.UnitTest;

/**
 * Unit test for {@link ChargebeeSubscriptionBillingConnector} against a mocked API client / config /
 * plan resolver.
 */
@UnitTest
public class ChargebeeSubscriptionBillingConnectorTest
{
	@Mock
	private ChargebeeApiClient apiClient;
	@Mock
	private ChargebeeConfigService configService;
	@Mock
	private ChargebeePlanResolver planResolver;

	private ChargebeeSubscriptionBillingConnector connector;

	@Before
	public void setUp()
	{
		MockitoAnnotations.openMocks(this);
		connector = new ChargebeeSubscriptionBillingConnector();
		connector.setApiClient(apiClient);
		connector.setConfigService(configService);
		connector.setPlanResolver(planResolver);
	}

	@Test
	public void platformIsChargebee()
	{
		assertEquals(BillingPlatform.CHARGEBEE, connector.platform());
	}

	@Test
	public void capabilitiesReflectChargebee()
	{
		final ConnectorCapabilities caps = connector.capabilities();
		assertFalse(caps.requiresNetworkTransactionId());
		assertTrue(caps.supportsImmediateStart());
		assertFalse(caps.supportsPause());
		assertTrue(caps.requiresPreConfiguredPlan());
		assertTrue(caps.liveTokenValidationOnImport());
		assertEquals(TokenImportStyle.SLASH_JOINED, caps.tokenImportStyle());
	}

	@Test
	public void configuredMerchantAccountComesFromConfig()
	{
		when(configService.getConfiguredAdyenMerchantAccount()).thenReturn("MERCH");
		assertEquals("MERCH", connector.configuredAdyenMerchantAccount());
	}

	@Test
	public void ensureCustomerReturnsRef() throws Exception
	{
		when(apiClient.ensureCustomer("cust", "e@x.com", "First", "Last")).thenReturn("cb-cust-1");

		final BillingCustomerRef ref = connector
				.ensureCustomer(new CustomerSyncRequest("cust", "e@x.com", "First", "Last", Map.of()));

		assertEquals(BillingPlatform.CHARGEBEE, ref.platform());
		assertEquals("cb-cust-1", ref.externalId());
	}

	@Test
	public void importAdyenTokenBuildsSlashJoinedReferenceId() throws Exception
	{
		when(configService.getConfiguredAdyenMerchantAccount()).thenReturn("MERCH");
		when(apiClient.importPermanentToken(any(), any(), any())).thenReturn("pm-1");
		final AdyenTokenHandle token = new AdyenTokenHandle("MERCH", "shopper-1", "TOK-1", null, null);

		final BillingPaymentMethodRef ref = connector.importAdyenToken(new TokenImportRequest(
				new BillingCustomerRef(BillingPlatform.CHARGEBEE, "cb-cust-1"), token, RecurringProcessingModel.SUBSCRIPTION));

		assertEquals("pm-1", ref.externalId());
		verify(apiClient).importPermanentToken("cb-cust-1", "shopper-1/TOK-1", null);
	}

	@Test
	public void importAdyenTokenRejectsMerchantAccountMismatch() throws Exception
	{
		when(configService.getConfiguredAdyenMerchantAccount()).thenReturn("OTHER");
		final AdyenTokenHandle token = new AdyenTokenHandle("MERCH", "shopper-1", "TOK-1", null, null);

		assertThrows(PreconditionFailedException.class, () -> connector.importAdyenToken(new TokenImportRequest(
				new BillingCustomerRef(BillingPlatform.CHARGEBEE, "cb-cust-1"), token, RecurringProcessingModel.SUBSCRIPTION)));
		verifyNoTokenImport();
	}

	@Test
	public void resolvePlanDelegatesToResolver() throws Exception
	{
		final PlanRef plan = new PlanRef("price-1", null);
		when(planResolver.resolve(any())).thenReturn(plan);

		assertSame(plan, connector.resolvePlan(new PlanResolutionRequest("PROD-1", Map.of())));
	}

	@Test
	public void createSubscriptionMapsPlanAndIdempotencyKey() throws Exception
	{
		when(apiClient.createSubscription(any())).thenReturn("sub-1");
		final SubscriptionCreateRequest request = new SubscriptionCreateRequest(
				new BillingCustomerRef(BillingPlatform.CHARGEBEE, "cb-cust-1"),
				new BillingPaymentMethodRef(BillingPlatform.CHARGEBEE, "pm-1"), new PlanRef("price-1", null), 2, null, "EUR",
				null, null, Map.of("sapOrderCode", "ORDER-1"), "ORDER-1");

		final BillingSubscriptionRef ref = connector.createSubscription(request);

		assertEquals("sub-1", ref.externalId());
		final ArgumentCaptor<ChargebeeSubscriptionParams> captor = ArgumentCaptor.forClass(ChargebeeSubscriptionParams.class);
		verify(apiClient).createSubscription(captor.capture());
		final ChargebeeSubscriptionParams params = captor.getValue();
		assertEquals("cb-cust-1", params.customerId());
		assertEquals("price-1", params.itemPriceId());
		assertEquals(2, params.quantity());
		assertEquals("ORDER-1", params.subscriptionId());
	}

	@Test
	public void fetchSubscriptionDelegatesToApiClient() throws Exception
	{
		final NormalizedSubscription snapshot = new NormalizedSubscription(
				new BillingSubscriptionRef(BillingPlatform.CHARGEBEE, "sub-1"), NormalizedSubscriptionStatus.ACTIVE,
				"price-1", 2, null, null, false, null);
		when(apiClient.fetchSubscription("sub-1")).thenReturn(snapshot);

		assertSame(snapshot,
				connector.fetchSubscription(new BillingSubscriptionRef(BillingPlatform.CHARGEBEE, "sub-1")));
	}

	@Test
	public void fetchSubscriptionRejectsNullReference() throws Exception
	{
		assertThrows(PreconditionFailedException.class, () -> connector.fetchSubscription(null));

		verify(apiClient, never()).fetchSubscription(any());
	}

	@Test
	public void fetchSubscriptionRejectsForeignPlatformReference() throws Exception
	{
		// A reference belonging to another platform must never be sent to Chargebee: subscription ids are
		// caller-chosen here, so it could silently address an unrelated Chargebee subscription.
		assertThrows(PreconditionFailedException.class,
				() -> connector.fetchSubscription(new BillingSubscriptionRef(BillingPlatform.RECURLY, "sub-1")));

		verify(apiClient, never()).fetchSubscription(any());
	}

	@Test
	public void updateSubscriptionUsesPlanIdNotPriceId() throws Exception
	{
		// guards the itemPriceId fix: the sendable id is planId; priceId must be ignored
		connector.updateSubscription(new SubscriptionUpdateRequest(
				new BillingSubscriptionRef(BillingPlatform.CHARGEBEE, "sub-1"), new PlanRef("price-9", "IGNORED"), 3, null,
				Map.of(), "k"));

		verify(apiClient).updateSubscription("sub-1", "price-9", 3);
	}

	@Test
	public void updateSubscriptionWithoutPlanPassesNullItemPrice() throws Exception
	{
		connector.updateSubscription(new SubscriptionUpdateRequest(
				new BillingSubscriptionRef(BillingPlatform.CHARGEBEE, "sub-1"), null, 5, null, Map.of(), "k"));

		verify(apiClient).updateSubscription("sub-1", null, 5);
	}

	@Test
	public void cancelSubscriptionMapsAtPeriodEnd() throws Exception
	{
		connector.cancelSubscription(new SubscriptionCancelRequest(
				new BillingSubscriptionRef(BillingPlatform.CHARGEBEE, "sub-1"), CancelReason.REQUESTED_BY_CUSTOMER,
				CancellationTiming.AT_PERIOD_END, "k"));

		verify(apiClient).cancelSubscription("sub-1", true);
	}

	/**
	 * The two timings reach the same Chargebee endpoint and differ only in {@code cancel_option}, so the
	 * flag survives one layer further down here than it does on Recurly. It still must not be reachable
	 * from the wrong timing.
	 */
	@Test
	public void cancelSubscriptionMapsImmediateCancellation() throws Exception
	{
		connector.cancelSubscription(new SubscriptionCancelRequest(
				new BillingSubscriptionRef(BillingPlatform.CHARGEBEE, "sub-1"), CancelReason.FRAUD,
				CancellationTiming.IMMEDIATELY, "k"));

		verify(apiClient).cancelSubscription("sub-1", false);
	}

	@Test
	public void pauseIsUnsupportedByDefault()
	{
		assertThrows(CapabilityUnsupportedException.class, () -> connector.pauseSubscription(
				new SubscriptionPauseRequest(new BillingSubscriptionRef(BillingPlatform.CHARGEBEE, "sub-1"), null, "k")));
	}

	@Test
	public void parseWebhookRejectsWhenCredentialsNotConfigured()
	{
		when(configService.getWebhookUsername()).thenReturn(null);

		assertThrows(PreconditionFailedException.class,
				() -> connector.parseWebhook(new RawWebhook(Map.of(AUTHORIZATION, basicAuth("u", "p")), "{}", null)));
	}

	@Test
	public void parseWebhookRejectsMissingAuthorizationHeader()
	{
		stubWebhookCredentials("cb-user", "cb-pass");

		assertThrows(TerminalBillingException.class,
				() -> connector.parseWebhook(new RawWebhook(Map.of(), "{}", null)));
	}

	@Test
	public void parseWebhookRejectsWrongCredentials()
	{
		stubWebhookCredentials("cb-user", "cb-pass");

		assertThrows(TerminalBillingException.class, () -> connector
				.parseWebhook(new RawWebhook(Map.of(AUTHORIZATION, basicAuth("cb-user", "wrong")), "{}", null)));
	}

	@Test
	public void parseWebhookAcceptsAuthorizationHeaderRegardlessOfCase() throws Exception
	{
		stubWebhookCredentials("cb-user", "cb-pass");

		final NormalizedBillingEvent event = connector.parseWebhook(new RawWebhook(
				Map.of("authorization", basicAuth("cb-user", "cb-pass")), unrecognizedEventPayload(), null));

		assertNull(event);
	}

	/**
	 * The fallback timestamp for an event Chargebee sent without {@code occurred_at} has to come from
	 * the injected clock, not from {@code Instant.now()}: otherwise nothing about the connector's
	 * sense of time - including the webhook lag it reports - can be asserted.
	 */
	@Test
	public void anEventWithoutOccurredAtIsStampedFromTheInjectedClock() throws Exception
	{
		stubWebhookCredentials("cb-user", "cb-pass");
		final Instant now = Instant.parse("2026-08-27T10:15:30Z");
		connector.setClock(Clock.fixed(now, ZoneOffset.UTC));
		final String payload = "{\"id\":\"ev_2\",\"event_type\":\"subscription_activated\","
				+ "\"content\":{\"subscription\":{\"id\":\"sub-9\",\"customer_id\":\"cust-9\"}}}";

		final NormalizedBillingEvent event = connector
				.parseWebhook(new RawWebhook(Map.of(AUTHORIZATION, basicAuth("cb-user", "cb-pass")), payload, null));

		assertEquals(now, event.occurredAt());
	}

	@Test
	public void parseWebhookRejectsMalformedJson()
	{
		stubWebhookCredentials("cb-user", "cb-pass");

		assertThrows(TerminalBillingException.class, () -> connector.parseWebhook(
				new RawWebhook(Map.of(AUTHORIZATION, basicAuth("cb-user", "cb-pass")), "not json", null)));
	}

	@Test
	public void parseWebhookReturnsNullForUnrecognizedEventType() throws Exception
	{
		stubWebhookCredentials("cb-user", "cb-pass");

		final NormalizedBillingEvent event = connector.parseWebhook(new RawWebhook(
				Map.of(AUTHORIZATION, basicAuth("cb-user", "cb-pass")), unrecognizedEventPayload(), null));

		assertNull(event);
	}

	@Test
	public void parseWebhookNormalizesSubscriptionActivated() throws Exception
	{
		stubWebhookCredentials("cb-user", "cb-pass");
		final String payload = "{"
				+ "\"id\":\"ev_1\",\"occurred_at\":1784628635,\"event_type\":\"subscription_activated\","
				+ "\"content\":{\"subscription\":{\"id\":\"sub-1\",\"customer_id\":\"cust-1\"}}}";

		final NormalizedBillingEvent event = connector
				.parseWebhook(new RawWebhook(Map.of(AUTHORIZATION, basicAuth("cb-user", "cb-pass")), payload, null));

		assertEquals(BillingPlatform.CHARGEBEE, event.platform());
		assertEquals(BillingEventType.SUBSCRIPTION_ACTIVATED, event.type());
		assertEquals("sub-1", event.externalSubscriptionId());
		assertEquals("cust-1", event.externalCustomerId());
		assertEquals(Instant.ofEpochSecond(1784628635L), event.occurredAt());
		assertEquals("ev_1", event.eventId());
	}

	@Test
	public void parseWebhookNormalizesPaymentSucceededAsInvoicePaid() throws Exception
	{
		stubWebhookCredentials("cb-user", "cb-pass");
		final String payload = "{"
				+ "\"id\":\"ev_2\",\"occurred_at\":1784628700,\"event_type\":\"payment_succeeded\","
				+ "\"content\":{\"transaction\":{\"subscription_id\":\"sub-2\",\"customer_id\":\"cust-2\"}}}";

		final NormalizedBillingEvent event = connector
				.parseWebhook(new RawWebhook(Map.of(AUTHORIZATION, basicAuth("cb-user", "cb-pass")), payload, null));

		assertEquals(BillingEventType.INVOICE_PAID, event.type());
		assertEquals("sub-2", event.externalSubscriptionId());
		assertEquals("cust-2", event.externalCustomerId());
	}

	@Test
	public void parseWebhookNormalizesPaymentFailedAsInvoicePaymentFailed() throws Exception
	{
		stubWebhookCredentials("cb-user", "cb-pass");
		final String payload = "{"
				+ "\"id\":\"ev_3\",\"occurred_at\":1784628800,\"event_type\":\"payment_failed\","
				+ "\"content\":{\"transaction\":{\"subscription_id\":\"sub-3\",\"customer_id\":\"cust-3\"}}}";

		final NormalizedBillingEvent event = connector
				.parseWebhook(new RawWebhook(Map.of(AUTHORIZATION, basicAuth("cb-user", "cb-pass")), payload, null));

		assertEquals(BillingEventType.INVOICE_PAYMENT_FAILED, event.type());
		assertEquals("sub-3", event.externalSubscriptionId());
	}

	@Test
	public void parseWebhookNormalizesSubscriptionCancelled() throws Exception
	{
		stubWebhookCredentials("cb-user", "cb-pass");
		final String payload = "{"
				+ "\"id\":\"ev_4\",\"occurred_at\":1784628900,\"event_type\":\"subscription_cancelled\","
				+ "\"content\":{\"subscription\":{\"id\":\"sub-4\",\"customer_id\":\"cust-4\"}}}";

		final NormalizedBillingEvent event = connector
				.parseWebhook(new RawWebhook(Map.of(AUTHORIZATION, basicAuth("cb-user", "cb-pass")), payload, null));

		assertEquals(BillingEventType.SUBSCRIPTION_CANCELLED, event.type());
		assertEquals("sub-4", event.externalSubscriptionId());
	}

	/**
	 * The event a subscription this connector creates actually announces. Chargebee books it with a start
	 * date and its own scheduler begins it, so {@code subscription_activated} — which marks a trial ending —
	 * never arrives, and for a long time this was the only lifecycle event mapped.
	 */
	@Test
	public void parseWebhookTreatsSubscriptionStartedAsAnActivation() throws Exception
	{
		assertEquals(BillingEventType.SUBSCRIPTION_ACTIVATED, typeOf("subscription_started"));
	}

	@Test
	public void parseWebhookTreatsReactivationAsAnActivation() throws Exception
	{
		assertEquals(BillingEventType.SUBSCRIPTION_ACTIVATED, typeOf("subscription_reactivated"));
	}

	@Test
	public void parseWebhookNormalizesSubscriptionChanged() throws Exception
	{
		assertEquals(BillingEventType.SUBSCRIPTION_UPDATED, typeOf("subscription_changed"));
	}

	/**
	 * The hosted portal's cancel button. Its own normalized type rather than the scheduled-plan-change one:
	 * the stored event type is the only trace of a delivery an operator sees, and these are different facts.
	 */
	@Test
	public void parseWebhookNormalizesAScheduledCancellation() throws Exception
	{
		assertEquals(BillingEventType.SUBSCRIPTION_CANCELLATION_SCHEDULED,
				typeOf("subscription_cancellation_scheduled"));
	}

	@Test
	public void parseWebhookNormalizesAWithdrawnScheduledCancellation() throws Exception
	{
		assertEquals(BillingEventType.SUBSCRIPTION_CANCELLATION_REMOVED,
				typeOf("subscription_scheduled_cancellation_removed"));
	}

	@Test
	public void parseWebhookNormalizesPauseAndResumeAlthoughPausingIsRefusedOnTheWayOut() throws Exception
	{
		assertEquals(BillingEventType.SUBSCRIPTION_PAUSED, typeOf("subscription_paused"));
		assertEquals(BillingEventType.SUBSCRIPTION_RESUMED, typeOf("subscription_resumed"));
	}

	/**
	 * A backdated operation is announced under its own event type instead of the plain one, so an unmapped
	 * variant is a hole in a lifecycle that otherwise looks covered. Note the spelling: Chargebee writes the
	 * backdated cancellation with one {@code l} where the base event has two.
	 */
	@Test
	public void parseWebhookRecognisesTheBackdatedSpellingsOfTheEventsItAlreadyMaps() throws Exception
	{
		assertEquals(BillingEventType.SUBSCRIPTION_CANCELLED, typeOf("subscription_canceled_with_backdating"));
		assertEquals(BillingEventType.SUBSCRIPTION_ACTIVATED, typeOf("subscription_activated_with_backdating"));
		assertEquals(BillingEventType.SUBSCRIPTION_ACTIVATED, typeOf("subscription_reactivated_with_backdating"));
		assertEquals(BillingEventType.SUBSCRIPTION_UPDATED, typeOf("subscription_changed_with_backdating"));
	}

	/**
	 * These are left unmapped on purpose, and saying so here is the only thing that tells the difference
	 * between a decision and an oversight.
	 *
	 * <p>The renewal is the one worth explaining: it is already covered, because a renewal charges the card
	 * and {@code payment_succeeded} carries the subscription id. Mapping it too would reconcile the same
	 * moment twice — and it maps onto a subscription-scoped type, so for every subscription on the site that
	 * this store did not create it would take the path that answers with an error and asks to be sent
	 * again, once per billing cycle. The rest have nowhere to land: the local projection holds no payment
	 * method, no scheduled plan change and no shipping address.</p>
	 */
	@Test
	public void parseWebhookStillIgnoresTheEventsWithNothingToProjectOnto() throws Exception
	{
		assertNull(typeOf("subscription_renewed"));
		assertNull(typeOf("subscription_items_renewed"));
		assertNull(typeOf("subscription_created"));
		assertNull(typeOf("subscription_changes_scheduled"));
		assertNull(typeOf("payment_source_updated"));
		assertNull(typeOf("customer_changed"));
	}

	private static final String AUTHORIZATION = "Authorization";

	/**
	 * Parses a minimal delivery of one vendor event type and returns the normalized type, or {@code null}
	 * where the connector acknowledges the delivery without acting on it.
	 */
	private BillingEventType typeOf(final String chargebeeEventType) throws Exception
	{
		stubWebhookCredentials("cb-user", "cb-pass");
		final String payload = "{\"id\":\"ev_" + chargebeeEventType + "\",\"occurred_at\":1784628635,"
				+ "\"event_type\":\"" + chargebeeEventType + "\","
				+ "\"content\":{\"subscription\":{\"id\":\"sub-1\",\"customer_id\":\"cust-1\"}}}";

		final NormalizedBillingEvent event = connector
				.parseWebhook(new RawWebhook(Map.of(AUTHORIZATION, basicAuth("cb-user", "cb-pass")), payload, null));
		return event == null ? null : event.type();
	}

	private void stubWebhookCredentials(final String username, final String password)
	{
		when(configService.getWebhookUsername()).thenReturn(username);
		when(configService.getWebhookPassword()).thenReturn(password);
	}

	private static String basicAuth(final String username, final String password)
	{
		return "Basic " + Base64.getEncoder()
				.encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
	}

	private static String unrecognizedEventPayload()
	{
		return "{\"id\":\"ev_x\",\"occurred_at\":1784628600,\"event_type\":\"invoice_generated\",\"content\":{}}";
	}

	private void verifyNoTokenImport() throws Exception
	{
		verify(apiClient, never()).importPermanentToken(any(), any(), any());
	}
}
