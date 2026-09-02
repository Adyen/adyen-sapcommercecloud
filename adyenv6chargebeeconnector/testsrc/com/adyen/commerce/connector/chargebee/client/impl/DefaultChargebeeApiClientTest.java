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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adyen.commerce.connector.chargebee.client.ChargebeeSubscriptionParams;
import com.adyen.commerce.connector.chargebee.config.ChargebeeConfigService;
import com.adyen.commerce.connector.chargebee.http.ChargebeeHttpClient;
import com.adyen.commerce.connector.chargebee.http.ChargebeeHttpResponse;
import com.adyen.commerce.connector.dto.CardMetadata;
import com.adyen.commerce.connector.dto.NormalizedSubscription;
import com.adyen.commerce.connector.dto.NormalizedSubscriptionStatus;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.PreconditionFailedException;
import com.adyen.commerce.connector.exception.RetryableBillingException;
import com.adyen.commerce.connector.exception.TerminalBillingException;

import de.hybris.bootstrap.annotations.UnitTest;

/**
 * Unit test for {@link DefaultChargebeeApiClient} against a mocked transport: auth header, endpoint
 * URLs, form-encoding and HTTP status &rarr; {@code BillingException} classification.
 */
@UnitTest
public class DefaultChargebeeApiClientTest
{
	private static final String BASE = "https://acme.chargebee.com/api/v2";

	@Mock
	private ChargebeeHttpClient httpClient;
	@Mock
	private ChargebeeConfigService configService;

	private DefaultChargebeeApiClient client;
	private String expectedAuth;

	@Before
	public void setUp() throws Exception
	{
		MockitoAnnotations.openMocks(this);
		client = new DefaultChargebeeApiClient();
		client.setHttpClient(httpClient);
		client.setConfigService(configService);
		when(configService.getApiBaseUrl()).thenReturn(BASE);
		when(configService.getApiKey()).thenReturn("cb-key");
		expectedAuth = "Basic " + Base64.getEncoder().encodeToString("cb-key:".getBytes(StandardCharsets.UTF_8));
	}

	@Test
	public void ensureCustomerReturnsExistingWithoutCreate() throws Exception
	{
		when(httpClient.get(BASE + "/customers/cust", expectedAuth))
				.thenReturn(new ChargebeeHttpResponse(200, "{\"customer\":{\"id\":\"cust\"}}"));

		assertEquals("cust", client.ensureCustomer("cust", "e@x.com", "F", "L"));
		verify(httpClient, never()).post(any(), any(), any(), any());
	}

	@Test
	public void ensureCustomerCreatesOnNotFound() throws Exception
	{
		when(httpClient.get(any(), any())).thenReturn(new ChargebeeHttpResponse(404, "{}"));
		when(httpClient.post(eq(BASE + "/customers"), eq(expectedAuth), any(), any()))
				.thenReturn(new ChargebeeHttpResponse(200, "{\"customer\":{\"id\":\"new-1\"}}"));

		assertEquals("new-1", client.ensureCustomer("cust", "e@x.com", "F", "L"));

		final ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
		verify(httpClient).post(eq(BASE + "/customers"), eq(expectedAuth), body.capture(), any());
		assertTrue(body.getValue().contains("id=cust"));
		assertTrue(body.getValue().contains("email=e%40x.com"));
	}

	@Test
	public void importPermanentTokenBuildsPayload() throws Exception
	{
		when(configService.getGatewayAccountId()).thenReturn("gw_adyen");
		when(httpClient.post(eq(BASE + "/payment_sources/create_using_permanent_token"), eq(expectedAuth), any(), any()))
				.thenReturn(new ChargebeeHttpResponse(200, "{\"payment_source\":{\"id\":\"pm-1\"}}"));

		final CardMetadata card = new CardMetadata("visa", "1111", "Alice", "03/2030", "credit");
		assertEquals("pm-1", client.importPermanentToken("cust", "shopper/TOK-1", card));

		final ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
		verify(httpClient).post(eq(BASE + "/payment_sources/create_using_permanent_token"), eq(expectedAuth),
				body.capture(), eq("shopper/TOK-1"));
		final String encoded = body.getValue();
		assertTrue(encoded.contains("customer_id=cust"));
		assertTrue(encoded.contains("type=card"));
		assertTrue(encoded.contains("gateway_account_id=gw_adyen"));
		assertTrue(encoded.contains("reference_id=shopper%2FTOK-1"));
		assertTrue(encoded.contains("replace_primary_payment_source=true"));
	}

	@Test
	public void mapsClientErrorToTerminal() throws Exception
	{
		when(httpClient.post(any(), any(), any(), any())).thenReturn(
				new ChargebeeHttpResponse(422, "{\"message\":\"bad token\",\"api_error_code\":\"invalid_request\"}"));

		final TerminalBillingException ex = assertThrows(TerminalBillingException.class,
				() -> client.importPermanentToken("cust", "shopper/TOK-1", null));
		assertTrue(ex.getMessage().contains("invalid_request"));
	}

	@Test
	public void mapsServerErrorToRetryable() throws Exception
	{
		when(httpClient.post(any(), any(), any(), any())).thenReturn(new ChargebeeHttpResponse(503, "{}"));

		assertThrows(RetryableBillingException.class, () -> client.importPermanentToken("cust", "shopper/TOK-1", null));
	}

	/**
	 * Chargebee's idempotency answering that the identical request is still in flight. Observed in
	 * production when the place-order path and Adyen's notification announced one order at the same
	 * moment: read as terminal it produced a dead letter saying the shopper was charged and has no
	 * subscription, a second before the winning caller created that very subscription.
	 */
	@Test
	public void mapsAnIdempotencyReplayInProgressToRetryable() throws Exception
	{
		when(httpClient.post(any(), any(), any(), any())).thenReturn(new ChargebeeHttpResponse(409,
				"{\"message\":\"The previous request is still in progress. Please retry the request\","
						+ "\"api_error_code\":\"invalid_state_for_request\"}"));

		assertThrows(RetryableBillingException.class, () -> client.importPermanentToken("cust", "shopper/TOK-1", null));
	}

	/**
	 * Other 409s are real conflicts about the state of a subscription; retrying those only postpones an
	 * unavoidable dead letter, so the status alone must not be enough to be retried.
	 */
	@Test
	public void mapsOtherConflictsToTerminal() throws Exception
	{
		when(httpClient.post(any(), any(), any(), any())).thenReturn(new ChargebeeHttpResponse(409,
				"{\"message\":\"Subscription is already cancelled\",\"api_error_code\":\"invalid_state\"}"));

		assertThrows(TerminalBillingException.class, () -> client.importPermanentToken("cust", "shopper/TOK-1", null));
	}

	/**
	 * A 409 whose body says nothing usable cannot be assumed to be the in-flight case.
	 */
	@Test
	public void mapsAConflictWithoutAnErrorCodeToTerminal() throws Exception
	{
		when(httpClient.post(any(), any(), any(), any())).thenReturn(new ChargebeeHttpResponse(409, ""));

		assertThrows(TerminalBillingException.class, () -> client.importPermanentToken("cust", "shopper/TOK-1", null));
	}

	@Test
	public void createSubscriptionEncodesItemPriceAndId() throws Exception
	{
		when(httpClient.post(eq(BASE + "/customers/cust/subscription_for_items"), eq(expectedAuth), any(), any()))
				.thenReturn(new ChargebeeHttpResponse(200, "{\"subscription\":{\"id\":\"sub-1\"}}"));

		final String id = client.createSubscription(
				new ChargebeeSubscriptionParams("cust", "price-1", 1, null, "ORDER-1", Map.of()));
		assertEquals("sub-1", id);

		final ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
		verify(httpClient).post(eq(BASE + "/customers/cust/subscription_for_items"), eq(expectedAuth), body.capture(),
				eq("ORDER-1"));
		final String encoded = body.getValue();
		assertTrue(encoded.contains("id=ORDER-1"));
		assertTrue(encoded.contains(
				URLEncoder.encode("subscription_items[item_price_id][0]", StandardCharsets.UTF_8) + "=price-1"));
		assertTrue(encoded.contains("auto_collection=on"));
	}

	@Test
	public void createSubscriptionClampsQuantityAndForwardsMetadata() throws Exception
	{
		when(httpClient.post(eq(BASE + "/customers/cust/subscription_for_items"), eq(expectedAuth), any(), any()))
				.thenReturn(new ChargebeeHttpResponse(200, "{\"subscription\":{\"id\":\"sub-1\"}}"));

		client.createSubscription(new ChargebeeSubscriptionParams("cust", "price-1", 0, null, "ORDER-1",
				Map.of("sapOrderCode", "ORDER-1")));

		final ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
		verify(httpClient).post(eq(BASE + "/customers/cust/subscription_for_items"), eq(expectedAuth), body.capture(),
				any());
		final String encoded = body.getValue();
		assertTrue(encoded.contains(URLEncoder.encode("subscription_items[quantity][0]", StandardCharsets.UTF_8) + "=1"));
		assertTrue(encoded.contains(URLEncoder.encode("meta_data[sapOrderCode]", StandardCharsets.UTF_8) + "=ORDER-1"));
	}

	@Test
	public void updateSubscriptionSendsOnlyProvidedFields() throws Exception
	{
		when(httpClient.post(eq(BASE + "/subscriptions/sub-1/update_for_items"), eq(expectedAuth), any(), any()))
				.thenReturn(new ChargebeeHttpResponse(200, "{\"subscription\":{\"id\":\"sub-1\"}}"));

		client.updateSubscription("sub-1", "price-2", null);

		final ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
		verify(httpClient).post(eq(BASE + "/subscriptions/sub-1/update_for_items"), eq(expectedAuth), body.capture(),
				any());
		final String encoded = body.getValue();
		assertTrue(encoded.contains(
				URLEncoder.encode("subscription_items[item_price_id][0]", StandardCharsets.UTF_8) + "=price-2"));
		assertTrue(!encoded.contains("quantity"));
	}

	@Test
	public void updateSubscriptionRejectsEmptyChange()
	{
		assertThrows(PreconditionFailedException.class, () -> client.updateSubscription("sub-1", null, null));
	}

	@Test
	public void updateSubscriptionRejectsQuantityWithoutItemPrice()
	{
		// Chargebee's update_for_items needs subscription_items[item_price_id][0] to anchor a quantity change;
		// a quantity-only call would 400 at the API ("item_price_id[0]: cannot be blank"). Surfaced by the live
		// Seen against the live sandbox: the guard turns that opaque remote 400 into a clear local
		// precondition.
		assertThrows(PreconditionFailedException.class, () -> client.updateSubscription("sub-1", null, 2));
	}

	@Test
	public void fetchSubscriptionMapsLiveState() throws Exception
	{
		stubRetrieve(subscriptionJson("active", ""));

		final NormalizedSubscription result = client.fetchSubscription("sub-1");

		assertEquals(BillingPlatform.CHARGEBEE, result.subscription().platform());
		assertEquals("sub-1", result.subscription().externalId());
		assertEquals(NormalizedSubscriptionStatus.ACTIVE, result.status());
		assertEquals("price-1", result.planId());
		assertEquals(Integer.valueOf(2), result.quantity());
		assertEquals(Instant.ofEpochSecond(1784541600L), result.currentPeriodStart());
		assertEquals(Instant.ofEpochSecond(1787133600L), result.currentPeriodEnd());
		assertEquals(Instant.ofEpochSecond(1784628635L), result.platformUpdatedAt());
		assertFalse(result.cancelAtPeriodEnd());
	}

	@Test
	public void fetchSubscriptionReadsPastDueFromTheSubscriptionItself() throws Exception
	{
		// Chargebee carries its dunning signal inside the subscription resource, so — unlike Recurly — no
		// second call is needed to find out that money is owed.
		stubRetrieve(subscriptionJson("active", ",\"due_invoices_count\":2,\"due_since\":1787133600"));

		assertEquals(NormalizedSubscriptionStatus.PAST_DUE, client.fetchSubscription("sub-1").status());
		verify(httpClient, times(1)).get(any(), any());
	}

	@Test
	public void fetchSubscriptionDoesNotReportPastDueForACancelledSubscription() throws Exception
	{
		stubRetrieve(subscriptionJson("cancelled", ",\"due_invoices_count\":1"));

		assertEquals(NormalizedSubscriptionStatus.EXPIRED, client.fetchSubscription("sub-1").status());
	}

	@Test
	public void fetchSubscriptionKeepsNonRenewingActiveAndFlagsCancelAtPeriodEnd() throws Exception
	{
		stubRetrieve(subscriptionJson("non_renewing", ""));

		final NormalizedSubscription result = client.fetchSubscription("sub-1");

		assertEquals(NormalizedSubscriptionStatus.ACTIVE, result.status());
		assertTrue(result.cancelAtPeriodEnd());
	}

	@Test
	public void fetchSubscriptionFlagsAScheduledCancellationOnAnActiveSubscription() throws Exception
	{
		stubRetrieve(subscriptionJson("active", ",\"cancel_schedule_created_at\":1787133600"));

		final NormalizedSubscription result = client.fetchSubscription("sub-1");

		assertEquals(NormalizedSubscriptionStatus.ACTIVE, result.status());
		assertTrue(result.cancelAtPeriodEnd());
	}

	@Test
	public void fetchSubscriptionMapsChargebeeLifecycleStates() throws Exception
	{
		assertEquals(NormalizedSubscriptionStatus.PENDING, statusOf("future"));
		assertEquals(NormalizedSubscriptionStatus.ACTIVE, statusOf("in_trial"));
		assertEquals(NormalizedSubscriptionStatus.ACTIVE, statusOf("active"));
		assertEquals(NormalizedSubscriptionStatus.PAUSED, statusOf("paused"));
		// EXPIRED rather than CANCELLED: Chargebee's "cancelled" means the subscription has stopped serving
		// the customer, which is what Recurly calls "expired". One word per situation across both adapters.
		assertEquals(NormalizedSubscriptionStatus.EXPIRED, statusOf("cancelled"));
		// transferred moves the subscription to another Chargebee site: neither cancelled nor expired here,
		// so it must stay UNKNOWN instead of being guessed at.
		assertEquals(NormalizedSubscriptionStatus.UNKNOWN, statusOf("transferred"));
	}

	@Test
	public void fetchSubscriptionMapsAnAbsentStatusToUnknown() throws Exception
	{
		stubRetrieve("{\"subscription\":{\"id\":\"sub-1\"}}");

		assertEquals(NormalizedSubscriptionStatus.UNKNOWN, client.fetchSubscription("sub-1").status());
	}

	@Test
	public void fetchSubscriptionTreatsMissingTimestampsAsNull() throws Exception
	{
		// Epoch 0 instead of null would date the subscription to 1970 and make the reconciliation staleness
		// guard discard every later snapshot.
		stubRetrieve("{\"subscription\":{\"id\":\"sub-1\",\"status\":\"future\"}}");

		final NormalizedSubscription result = client.fetchSubscription("sub-1");

		assertNull(result.currentPeriodStart());
		assertNull(result.currentPeriodEnd());
		assertNull(result.platformUpdatedAt());
		assertNull(result.planId());
		assertEquals(Integer.valueOf(1), result.quantity());
		assertEquals(NormalizedSubscriptionStatus.PENDING, result.status());
	}

	@Test
	public void fetchSubscriptionRejectsANonEpochTimestamp() throws Exception
	{
		stubRetrieve("{\"subscription\":{\"id\":\"sub-1\",\"status\":\"active\","
				+ "\"updated_at\":\"2026-08-20T10:00:00Z\"}}");

		assertThrows(TerminalBillingException.class, () -> client.fetchSubscription("sub-1"));
	}

	@Test
	public void fetchSubscriptionPicksThePlanItemAmongAddons() throws Exception
	{
		stubRetrieve("{\"subscription\":{\"id\":\"sub-1\",\"status\":\"active\",\"subscription_items\":["
				+ "{\"item_price_id\":\"addon-1\",\"item_type\":\"addon\",\"quantity\":7},"
				+ "{\"item_price_id\":\"price-1\",\"item_type\":\"plan\",\"quantity\":2}]}}");

		final NormalizedSubscription result = client.fetchSubscription("sub-1");

		assertEquals("price-1", result.planId());
		assertEquals(Integer.valueOf(2), result.quantity());
	}

	@Test
	public void fetchSubscriptionRejectsAResponseWithoutId() throws Exception
	{
		stubRetrieve("{\"subscription\":{\"status\":\"active\"}}");

		assertThrows(TerminalBillingException.class, () -> client.fetchSubscription("sub-1"));
	}

	@Test
	public void fetchSubscriptionMapsNotFoundToTerminal() throws Exception
	{
		when(httpClient.get(BASE + "/subscriptions/sub-1", expectedAuth)).thenReturn(new ChargebeeHttpResponse(404,
				"{\"message\":\"resource not found\",\"api_error_code\":\"resource_not_found\"}"));

		final TerminalBillingException ex = assertThrows(TerminalBillingException.class,
				() -> client.fetchSubscription("sub-1"));
		assertTrue(ex.getMessage().contains("retrieve subscription"));
	}

	@Test
	public void fetchSubscriptionMapsServerErrorToRetryable() throws Exception
	{
		when(httpClient.get(BASE + "/subscriptions/sub-1", expectedAuth))
				.thenReturn(new ChargebeeHttpResponse(502, "{}"));

		assertThrows(RetryableBillingException.class, () -> client.fetchSubscription("sub-1"));
	}

	private NormalizedSubscriptionStatus statusOf(final String chargebeeStatus) throws Exception
	{
		stubRetrieve(subscriptionJson(chargebeeStatus, ""));
		return client.fetchSubscription("sub-1").status();
	}

	private void stubRetrieve(final String body) throws Exception
	{
		when(httpClient.get(BASE + "/subscriptions/sub-1", expectedAuth))
				.thenReturn(new ChargebeeHttpResponse(200, body));
	}

	/**
	 * Shape of Chargebee's retrieve-a-subscription response: timestamps are UNIX epoch seconds and the plan
	 * lives in {@code subscription_items} as the entry with {@code item_type=plan}.
	 */
	private static String subscriptionJson(final String status, final String extraFields)
	{
		return "{\"subscription\":{\"id\":\"sub-1\",\"customer_id\":\"cust\",\"status\":\"" + status + "\","
				+ "\"current_term_start\":1784541600,\"current_term_end\":1787133600,\"updated_at\":1784628635,"
				+ "\"subscription_items\":[{\"item_price_id\":\"price-1\",\"item_type\":\"plan\",\"quantity\":2}]"
				+ extraFields + "}}";
	}
}
