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
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adyen.commerce.connector.chargebee.config.ChargebeeConfigService;
import com.adyen.commerce.connector.chargebee.http.ChargebeeHttpClient;
import com.adyen.commerce.connector.chargebee.http.ChargebeeHttpResponse;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.RetryableBillingException;
import com.adyen.commerce.connector.log.ConnectorLogEvent;

/**
 * httpclient5-based transport (reuses the client jar provided by adyenv6core). IOExceptions are
 * treated as transient and surfaced as {@link RetryableBillingException}.
 */
public class DefaultChargebeeHttpClient implements ChargebeeHttpClient
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultChargebeeHttpClient.class);
	private static final String EVENT_CONNECTOR_CALL = "connector_call";
	private static final String IDEMPOTENCY_KEY_HEADER = "chargebee-idempotency-key";

	private volatile CloseableHttpClient httpClient;
	private ChargebeeConfigService configService;

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
			post.setHeader(IDEMPOTENCY_KEY_HEADER, idempotencyKey);
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
		final long startedAt = System.nanoTime();
		try
		{
			final ChargebeeHttpResponse result = getHttpClient().execute(request, response -> {
				final String body = response.getEntity() == null ? ""
						: EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
				return new ChargebeeHttpResponse(response.getCode(), body);
			});
			// No retryable= here: whether this call will be retried is decided one layer up, by the API
			// client, on the status *and* the vendor error code. A second opinion formed from the status
			// alone would contradict it on exactly the interesting case (409 invalid_state_for_request).
			transportEvent(request)
					.outcome(result.isSuccess()
							? ConnectorLogEvent.OUTCOME_SUCCESS
							: ConnectorLogEvent.OUTCOME_FAILURE)
					.durationSince(startedAt)
					.field("http_status", Integer.valueOf(result.statusCode()))
					.field("error_class", ConnectorLogEvent.httpErrorClass(result.statusCode()))
					.log(LOG, !result.isSuccess());
			return result;
		}
		catch (final IOException e)
		{
			transportEvent(request)
					.outcome(ConnectorLogEvent.OUTCOME_FAILURE)
					.durationSince(startedAt)
					.field("error_class", classifyException(e))
					.field("exception_class", e.getClass().getName())
					.warn(LOG);
			throw new RetryableBillingException("Chargebee HTTP call to " + url + " failed", e);
		}
	}

	/**
	 * The transport deliberately does not name the business operation: it cannot know one, and the
	 * surrounding {@code ConnectorLogContext} scope already supplies it. Earlier this was inferred from
	 * the URL shape, which is a guess that is usually right - and nothing downstream can tell those apart
	 * from the times it is wrong.
	 */
	private ConnectorLogEvent transportEvent(final HttpUriRequestBase request)
	{
		return ConnectorLogEvent.of(EVENT_CONNECTOR_CALL)
				.platform(BillingPlatform.CHARGEBEE)
				.field("method", request.getMethod())
				.field("idempotency_key_present",
						Boolean.valueOf(request.containsHeader(IDEMPOTENCY_KEY_HEADER)));
	}

	private static String classifyException(final IOException error)
	{
		return error.getClass().getSimpleName().toLowerCase(Locale.ROOT).contains("timeout")
				? "timeout" : "connection";
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
					final RequestConfig requestConfig = RequestConfig.custom()
							.setConnectTimeout(Timeout.ofMilliseconds(configService.getConnectTimeoutMillis()))
							// httpclient5 has NO default response timeout: without this a Chargebee call that
							// connects and then hangs holds a platform worker thread forever.
							.setResponseTimeout(Timeout.ofMilliseconds(configService.getResponseTimeoutMillis()))
							// Left at the default the wait for a free pooled connection is three minutes, which
							// silently becomes the real worst case a caller sees regardless of the two above.
							.setConnectionRequestTimeout(
									Timeout.ofMilliseconds(configService.getConnectionRequestTimeoutMillis()))
							.build();
					// Every call targets the one Chargebee host, so the default per-route cap of 2 would
					// serialize the whole platform onto two connections.
					final PoolingHttpClientConnectionManager connectionManager =
							PoolingHttpClientConnectionManagerBuilder.create()
									.setMaxConnTotal(configService.getMaxConnections())
									.setMaxConnPerRoute(configService.getMaxConnections())
									.build();
					client = HttpClients.custom().useSystemProperties()
							.setConnectionManager(connectionManager)
							.setDefaultRequestConfig(requestConfig)
							.build();
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

	public void setConfigService(final ChargebeeConfigService configService)
	{
		this.configService = configService;
	}
}
