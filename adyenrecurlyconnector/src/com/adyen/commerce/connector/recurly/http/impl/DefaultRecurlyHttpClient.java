package com.adyen.commerce.connector.recurly.http.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import com.adyen.commerce.connector.exception.ConnectorNotConfiguredException;
import com.adyen.commerce.connector.exception.RetryableBillingException;
import com.adyen.commerce.connector.recurly.config.RecurlyConfigService;
import com.adyen.commerce.connector.recurly.http.RecurlyHttpClient;
import com.adyen.commerce.connector.recurly.http.RecurlyHttpResponse;
import org.apache.hc.core5.http.io.entity.StringEntity;

public class DefaultRecurlyHttpClient implements RecurlyHttpClient
{
    private static final String ACCEPT_HEADER =
            "application/vnd.recurly.v2021-02-25+json";

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final CloseableHttpClient httpClient =
            HttpClients.createSystem();

    private RecurlyConfigService recurlyConfigService;

    @Override
    public RecurlyHttpResponse post(
            final String path,
            final String jsonBody,
            final String idempotencyKey)
            throws RetryableBillingException, ConnectorNotConfiguredException
    {
        final String url =
                recurlyConfigService.getBaseUrl() + normalizePath(path);

        final HttpPost request = new HttpPost(url);

        request.setEntity(new StringEntity(
                jsonBody,
                ContentType.APPLICATION_JSON));

        if (StringUtils.isNotBlank(idempotencyKey))
        {
            request.setHeader(
                    IDEMPOTENCY_KEY_HEADER,
                    idempotencyKey);
        }

        return execute(request, url);
    }

    @Override
    public RecurlyHttpResponse get(final String path)
            throws RetryableBillingException, ConnectorNotConfiguredException
    {
        final String url =
                recurlyConfigService.getBaseUrl() + normalizePath(path);

        return execute(new HttpGet(url), url);
    }

    protected RecurlyHttpResponse execute(
            final HttpUriRequestBase request,
            final String url)
            throws RetryableBillingException, ConnectorNotConfiguredException
    {
        request.setHeader(
                HttpHeaders.AUTHORIZATION,
                createAuthorizationHeader());

        request.setHeader(
                HttpHeaders.ACCEPT,
                ACCEPT_HEADER);

        try
        {
            return httpClient.execute(request, response -> {
                final String body = response.getEntity() == null
                        ? ""
                        : EntityUtils.toString(
                        response.getEntity(),
                        StandardCharsets.UTF_8);

                return new RecurlyHttpResponse(
                        response.getCode(),
                        body);
            });
        }
        catch (final IOException exception)
        {
            throw new RetryableBillingException(
                    "Recurly HTTP call to " + url + " failed",
                    exception);
        }
    }

    protected String createAuthorizationHeader()
            throws ConnectorNotConfiguredException
    {
        final String credentials =
                recurlyConfigService.getApiKey() + ":";

        final String encodedCredentials = Base64.getEncoder()
                .encodeToString(
                        credentials.getBytes(StandardCharsets.UTF_8));

        return "Basic " + encodedCredentials;
    }

    protected String normalizePath(final String path)
    {
        return path.startsWith("/") ? path : "/" + path;
    }

    public void close() throws IOException
    {
        httpClient.close();
    }

    public void setRecurlyConfigService(
            final RecurlyConfigService recurlyConfigService)
    {
        this.recurlyConfigService = recurlyConfigService;
    }
}