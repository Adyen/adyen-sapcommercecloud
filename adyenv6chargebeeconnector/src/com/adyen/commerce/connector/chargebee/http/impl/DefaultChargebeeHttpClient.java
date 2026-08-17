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
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.adyen.commerce.connector.chargebee.http.ChargebeeHttpClient;
import com.adyen.commerce.connector.chargebee.http.ChargebeeHttpResponse;
import com.adyen.commerce.connector.exception.RetryableBillingException;

/**
 * httpclient5-based transport (reuses the client jar provided by adyenv6core). IOExceptions are
 * treated as transient and surfaced as {@link RetryableBillingException}.
 */
public class DefaultChargebeeHttpClient implements ChargebeeHttpClient
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultChargebeeHttpClient.class);
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
		final String operation = operation(request);
		final boolean idempotent = request.containsHeader("chargebee-idempotency-key");
		final long startedAt = System.nanoTime();
		try
		{
			final ChargebeeHttpResponse result = getHttpClient().execute(request, response -> {
				final String body = response.getEntity() == null ? ""
						: EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
				return new ChargebeeHttpResponse(response.getCode(), body);
			});
			final long durationMs = elapsedMillis(startedAt);
			final String outcome = result.isSuccess() ? "success" : "failure";
			final String errorClass = classifyStatus(result.statusCode());
			final boolean retryable = result.statusCode() == 429 || result.statusCode() >= 500;
			if (result.isSuccess())
			{
				LOG.info("event=connector_call platform=CHARGEBEE operation={} method={} outcome={} duration_ms={} "
						+ "http_status={} error_class={} retryable={} idempotency_key_present={} correlation_id={}",
						operation, request.getMethod(), outcome, durationMs, result.statusCode(), errorClass, retryable,
						idempotent, correlationId());
			}
			else
			{
				LOG.warn("event=connector_call platform=CHARGEBEE operation={} method={} outcome={} duration_ms={} "
						+ "http_status={} error_class={} retryable={} idempotency_key_present={} correlation_id={}",
						operation, request.getMethod(), outcome, durationMs, result.statusCode(), errorClass, retryable,
						idempotent, correlationId());
			}
			return result;
		}
		catch (final IOException e)
		{
			LOG.warn("event=connector_call platform=CHARGEBEE operation={} method={} outcome=failure duration_ms={} "
					+ "http_status=none error_class={} exception_class={} retryable=true "
					+ "idempotency_key_present={} correlation_id={}", operation, request.getMethod(), elapsedMillis(startedAt),
					classifyException(e), e.getClass().getName(), idempotent, correlationId());
			throw new RetryableBillingException("Chargebee HTTP operation '" + operation + "' failed", e);
		}
	}

	protected String operation(final HttpUriRequestBase request)
	{
		final String path = StringUtils.defaultString(request.getPath()).toLowerCase(Locale.ROOT);
		if (path.contains("/payment_sources/create_using_permanent_token")) return "import_token";
		if (path.contains("/subscription_for_items")) return "create_subscription";
		if (path.contains("/update_for_items")) return "update_subscription";
		if (path.contains("/cancel_for_items")) return "cancel_subscription";
		if (path.contains("/customers")) return "ensure_customer";
		return "http_" + request.getMethod().toLowerCase(Locale.ROOT);
	}

	private static String classifyStatus(final int status)
	{
		if (status >= 200 && status < 300) return "none";
		if (status == 429) return "rate_limit";
		if (status >= 500) return "remote_5xx";
		if (status >= 400) return "remote_4xx";
		return "unexpected_status";
	}

	private static String classifyException(final IOException error)
	{
		return error.getClass().getSimpleName().toLowerCase(Locale.ROOT).contains("timeout")
				? "timeout" : "connection";
	}

	private static long elapsedMillis(final long startedAt)
	{
		return (System.nanoTime() - startedAt) / 1_000_000L;
	}

	private static String correlationId()
	{
		return StringUtils.defaultIfBlank(MDC.get("correlationId"), "none");
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
