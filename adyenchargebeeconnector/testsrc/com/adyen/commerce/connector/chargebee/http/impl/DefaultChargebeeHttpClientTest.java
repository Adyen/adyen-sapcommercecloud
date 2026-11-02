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
package com.adyen.commerce.connector.chargebee.http.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adyen.commerce.connector.chargebee.http.ChargebeeHttpResponse;

import de.hybris.bootstrap.annotations.UnitTest;

/**
 * Unit test for {@link DefaultChargebeeHttpClient}: what the request it hands to httpclient carries.
 */
@UnitTest
public class DefaultChargebeeHttpClientTest
{
	private static final String URL = "https://acme.chargebee.com/api/v2/subscriptions/sub-1/cancel_for_items";
	private static final String IDEMPOTENCY_KEY_HEADER = "chargebee-idempotency-key";

	@Mock
	private CloseableHttpClient httpClient;

	private final List<ClassicHttpRequest> sent = new ArrayList<>();
	private DefaultChargebeeHttpClient client;

	@Before
	public void setUp() throws Exception
	{
		MockitoAnnotations.openMocks(this);
		when(httpClient.execute(any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class)))
				.thenAnswer(invocation -> {
					sent.add(invocation.getArgument(0));
					return new ChargebeeHttpResponse(200, "{}");
				});
		client = new DefaultChargebeeHttpClient();
		client.setHttpClient(httpClient);
	}

	@Test
	public void postSendsTheIdempotencyKey() throws Exception
	{
		client.post(URL, "Basic x", "cancel_option=end_of_term", "ORDER-1/at_period_end");

		final Header header = sent.get(0).getFirstHeader(IDEMPOTENCY_KEY_HEADER);
		assertNotNull(header);
		assertEquals("ORDER-1/at_period_end", header.getValue());
	}

	@Test
	public void postWithABlankKeySendsNoIdempotencyHeader() throws Exception
	{
		client.post(URL, "Basic x", "cancel_option=end_of_term", null);
		client.post(URL, "Basic x", "cancel_option=end_of_term", "");
		client.post(URL, "Basic x", "cancel_option=end_of_term", "  ");

		assertEquals(3, sent.size());
		for (final ClassicHttpRequest request : sent)
		{
			assertFalse(request.containsHeader(IDEMPOTENCY_KEY_HEADER));
		}
	}
}
