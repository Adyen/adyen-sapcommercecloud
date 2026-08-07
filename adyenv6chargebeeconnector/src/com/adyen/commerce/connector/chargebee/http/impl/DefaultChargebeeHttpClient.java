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

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;

import com.adyen.commerce.connector.chargebee.http.ChargebeeHttpClient;
import com.adyen.commerce.connector.chargebee.http.ChargebeeHttpResponse;
import com.adyen.commerce.connector.exception.RetryableBillingException;

/**
 * httpclient5-based transport (reuses the client jar provided by adyenv6core). IOExceptions are
 * treated as transient and surfaced as {@link RetryableBillingException}.
 */
public class DefaultChargebeeHttpClient implements ChargebeeHttpClient
{
	private volatile CloseableHttpClient httpClient;

	private static final ContentType FORM_UTF8 = ContentType.create("application/x-www-form-urlencoded",
			StandardCharsets.UTF_8);

	@Override
	public ChargebeeHttpResponse post(final String url, final String authorizationHeader, final String formBody,
			final String idempotencyKey) throws RetryableBillingException
	{
		final HttpPost post = new HttpPost(url);
		post.setEntity(new StringEntity(formBody == null ? "" : formBody, FORM_UTF8));
		if (StringUtils.isNotBlank(idempotencyKey))
		{
			post.setHeader("chargebee-idempotency-key", idempotencyKey);
		}
		return execute(post, url, authorizationHeader);
	}

	@Override
	public ChargebeeHttpResponse get(final String url, final String authorizationHeader) throws RetryableBillingException
	{
		return execute(new HttpGet(url), url, authorizationHeader);
	}

	protected ChargebeeHttpResponse execute(final HttpUriRequestBase request, final String url,
			final String authorizationHeader) throws RetryableBillingException
	{
		request.setHeader(HttpHeaders.AUTHORIZATION, authorizationHeader);
		request.setHeader(HttpHeaders.ACCEPT, "application/json");
		try
		{
			return getHttpClient().execute(request, response -> {
				final String body = response.getEntity() == null ? ""
						: EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
				return new ChargebeeHttpResponse(response.getCode(), body);
			});
		}
		catch (final IOException e)
		{
			throw new RetryableBillingException("Chargebee HTTP call to " + url + " failed: " + e.getMessage(), e);
		}
	}

	protected CloseableHttpClient getHttpClient()
	{
		CloseableHttpClient client = httpClient;
		if (client == null)
		{
			synchronized (this)
			{
				client = httpClient;
				if (client == null)
				{
					client = HttpClients.createSystem();
					httpClient = client;
				}
			}
		}
		return client;
	}

	public void setHttpClient(final CloseableHttpClient httpClient)
	{
		this.httpClient = httpClient;
	}
}
