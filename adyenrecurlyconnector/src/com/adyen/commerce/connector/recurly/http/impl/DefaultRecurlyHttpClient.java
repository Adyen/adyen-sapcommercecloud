package com.adyen.commerce.connector.recurly.http.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
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

import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.RetryableBillingException;
import com.adyen.commerce.connector.log.ConnectorLogEvent;
import com.adyen.commerce.connector.recurly.config.RecurlyConfigService;
import com.adyen.commerce.connector.recurly.http.RecurlyHttpClient;
import com.adyen.commerce.connector.recurly.http.RecurlyHttpResponse;

/** httpclient5 transport. An {@link IOException} is transient and surfaces as {@link RetryableBillingException}. */
public class DefaultRecurlyHttpClient implements RecurlyHttpClient {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultRecurlyHttpClient.class);
    private static final String EVENT_CONNECTOR_CALL = "connector_call";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private static final ContentType JSON_UTF8 = ContentType.create("application/json", StandardCharsets.UTF_8);

    private volatile CloseableHttpClient httpClient;
    private RecurlyConfigService configService;

    @Override
    public RecurlyHttpResponse get(final String url, final String authorizationHeader, final String acceptHeader)
            throws RetryableBillingException {
        return execute(new HttpGet(url), url, authorizationHeader, acceptHeader, null);
    }

    @Override
    public RecurlyHttpResponse post(final String url, final String authorizationHeader, final String acceptHeader,
                                    final String jsonBody, final String idempotencyKey)
            throws RetryableBillingException {
        final HttpPost request = new HttpPost(url);
        request.setEntity(new StringEntity(jsonBody == null ? "{}" : jsonBody, JSON_UTF8));
        return execute(request, url, authorizationHeader, acceptHeader, idempotencyKey);
    }

    @Override
    public RecurlyHttpResponse put(final String url, final String authorizationHeader, final String acceptHeader,
                                   final String jsonBody, final String idempotencyKey)
            throws RetryableBillingException {
        final HttpPut request = new HttpPut(url);
        request.setEntity(new StringEntity(jsonBody == null ? "{}" : jsonBody, JSON_UTF8));
        return execute(request, url, authorizationHeader, acceptHeader, idempotencyKey);
    }

    @Override
    public RecurlyHttpResponse delete(final String url, final String authorizationHeader, final String acceptHeader,
                                      final String idempotencyKey) throws RetryableBillingException {
        return execute(new HttpDelete(url), url, authorizationHeader, acceptHeader, idempotencyKey);
    }

    protected RecurlyHttpResponse execute(final HttpUriRequestBase request, final String url,
                                          final String authorizationHeader, final String acceptHeader,
                                          final String idempotencyKey) throws RetryableBillingException {
        request.setHeader(HttpHeaders.AUTHORIZATION, authorizationHeader);
        request.setHeader(HttpHeaders.ACCEPT, acceptHeader);
        request.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        if (StringUtils.isNotBlank(idempotencyKey)) {
            request.setHeader(IDEMPOTENCY_KEY_HEADER, idempotencyKey);
        }
        final long startedAt = System.nanoTime();
        try {
            final RecurlyHttpResponse result = getHttpClient().execute(request, response ->
            {
                final String body = response.getEntity() == null
                        ? ""
                        : EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                return new RecurlyHttpResponse(response.getCode(), body);
            });
            // No retryable= here: the API client decides retry from the status and the vendor error code.
            transportEvent(request, idempotencyKey)
                    .outcome(result.isSuccess()
                            ? ConnectorLogEvent.OUTCOME_SUCCESS
                            : ConnectorLogEvent.OUTCOME_FAILURE)
                    .durationSince(startedAt)
                    .field("http_status", result.statusCode())
                    .field("error_class", ConnectorLogEvent.httpErrorClass(result.statusCode()))
                    .log(LOG, !result.isSuccess());
            return result;
        } catch (final IOException e) {
            transportEvent(request, idempotencyKey)
                    .outcome(ConnectorLogEvent.OUTCOME_FAILURE)
                    .durationSince(startedAt)
                    .field("error_class", classifyException(e))
                    .field("exception_class", e.getClass().getName())
                    .warn(LOG);
            throw new RetryableBillingException("Recurly HTTP call to " + url + " failed", e);
        }
    }

    /** No operation name: the surrounding {@code ConnectorLogContext} scope supplies it. */
    private ConnectorLogEvent transportEvent(final HttpUriRequestBase request, final String idempotencyKey) {
        return ConnectorLogEvent.of(EVENT_CONNECTOR_CALL)
                .platform(BillingPlatform.RECURLY)
                .field("method", request.getMethod())
                .field("idempotency_key_present", StringUtils.isNotBlank(idempotencyKey));
    }

    private static String classifyException(final IOException error) {
        final String type = error.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        return type.contains("timeout") ? "timeout" : "connection";
    }

    protected CloseableHttpClient getHttpClient() {
        CloseableHttpClient client = httpClient;
        if (client == null) {
            synchronized (this) {
                client = httpClient;
                if (client == null) {
                    final RequestConfig requestConfig = RequestConfig.custom()
                            .setConnectTimeout(Timeout.ofMilliseconds(configService.getConnectTimeoutMillis()))
                            .setResponseTimeout(Timeout.ofMilliseconds(configService.getResponseTimeoutMillis()))
                            // The default lease wait is three minutes, longer than any other timeout.
                            .setConnectionRequestTimeout(
                                    Timeout.ofMilliseconds(configService.getConnectionRequestTimeoutMillis()))
                            .build();
                    // Every call targets one host, so the default per-route cap of 2 would serialize them.
                    final PoolingHttpClientConnectionManager connectionManager =
                            PoolingHttpClientConnectionManagerBuilder.create()
                                    .setMaxConnTotal(configService.getMaxConnections())
                                    .setMaxConnPerRoute(configService.getMaxConnections())
                                    .build();
                    client = HttpClients.custom().useSystemProperties()
                            .setConnectionManager(connectionManager)
                            .setDefaultRequestConfig(requestConfig).build();
                    httpClient = client;
                }
            }
        }
        return client;
    }

    public void setConfigService(final RecurlyConfigService configService) {
        this.configService = configService;
    }
}
