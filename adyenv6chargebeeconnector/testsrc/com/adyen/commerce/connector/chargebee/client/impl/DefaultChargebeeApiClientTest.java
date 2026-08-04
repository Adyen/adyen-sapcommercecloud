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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
}
