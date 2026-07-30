package com.adyen.commerce.connector.recurly.http.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPatch;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;

import com.adyen.commerce.connector.exception.RetryableBillingException;
import com.adyen.commerce.connector.recurly.config.RecurlyConfigService;
import com.adyen.commerce.connector.recurly.http.RecurlyHttpClient;
import com.adyen.commerce.connector.recurly.http.RecurlyHttpResponse;

/**
 * httpclient5-based transport. IOExceptions are treated as transient and surfaced as
 * {@link RetryableBillingException}.
 */
public class DefaultRecurlyHttpClient implements RecurlyHttpClient
{
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private volatile CloseableHttpClient httpClient;
    private final RecurlyConfigService configService;

    private static final ContentType JSON_UTF8 = ContentType.create("application/json", StandardCharsets.UTF_8);

    public DefaultRecurlyHttpClient(final RecurlyConfigService configService)
    {
        this.configService = configService;
    }

    @Override
    public RecurlyHttpResponse get(final String url, final String authorizationHeader, final String acceptHeader)
            throws RetryableBillingException
    {
        return execute(new HttpGet(url), url, authorizationHeader, acceptHeader, null);
    }

    @Override
    public RecurlyHttpResponse post(final String url, final String authorizationHeader, final String acceptHeader,
                                    final String jsonBody, final String idempotencyKey)
            throws RetryableBillingException
    {
        final HttpPost request = new HttpPost(url);
        request.setEntity(new StringEntity(jsonBody == null ? "{}" : jsonBody, JSON_UTF8));
        return execute(request, url, authorizationHeader, acceptHeader, idempotencyKey);
    }

    @Override
    public RecurlyHttpResponse patch(final String url, final String authorizationHeader, final String acceptHeader,
                                     final String jsonBody, final String idempotencyKey)
            throws RetryableBillingException
    {
        final HttpPatch request = new HttpPatch(url);
        request.setEntity(new StringEntity(jsonBody == null ? "{}" : jsonBody, JSON_UTF8));
        return execute(request, url, authorizationHeader, acceptHeader, idempotencyKey);
    }

    @Override
    public RecurlyHttpResponse put(final String url, final String authorizationHeader, final String acceptHeader,
                                   final String jsonBody, final String idempotencyKey)
            throws RetryableBillingException
    {
        final HttpPut request = new HttpPut(url);
        request.setEntity(new StringEntity(jsonBody == null ? "{}" : jsonBody, JSON_UTF8));
        return execute(request, url, authorizationHeader, acceptHeader, idempotencyKey);
    }

    @Override
    public RecurlyHttpResponse delete(final String url, final String authorizationHeader, final String acceptHeader,
                                      final String idempotencyKey) throws RetryableBillingException
    {
        return execute(new HttpDelete(url), url, authorizationHeader, acceptHeader, idempotencyKey);
    }

    protected RecurlyHttpResponse execute(final HttpUriRequestBase request, final String url,
                                          final String authorizationHeader, final String acceptHeader,
                                          final String idempotencyKey) throws RetryableBillingException
    {
        request.setHeader(HttpHeaders.AUTHORIZATION, authorizationHeader);
        request.setHeader(HttpHeaders.ACCEPT, acceptHeader);
        request.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        if (StringUtils.isNotBlank(idempotencyKey))
        {
            request.setHeader(IDEMPOTENCY_KEY_HEADER, idempotencyKey);
        }
        try
        {
            return getHttpClient().execute(request, response ->
            {
                final String body = response.getEntity() == null
                        ? ""
                        : EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                return new RecurlyHttpResponse(response.getCode(), body);
            });
        }
        catch (final IOException e)
        {
            throw new RetryableBillingException("Recurly HTTP call to " + url + " failed: " + e.getMessage(), e);
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
                    final RequestConfig requestConfig = RequestConfig.custom()
                            .setConnectTimeout(Timeout.ofMilliseconds(configService.getConnectTimeoutMillis()))
                            .setResponseTimeout(Timeout.ofMilliseconds(configService.getResponseTimeoutMillis()))
                            .build();
                    client = HttpClients.custom().useSystemProperties().setDefaultRequestConfig(requestConfig).build();
                    httpClient = client;
                }
            }
        }
        return client;
    }

}
