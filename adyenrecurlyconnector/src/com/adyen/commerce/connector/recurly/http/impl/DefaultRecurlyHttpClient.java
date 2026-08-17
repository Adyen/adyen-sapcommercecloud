package com.adyen.commerce.connector.recurly.http.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

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
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.adyen.commerce.connector.exception.RetryableBillingException;
import com.adyen.commerce.connector.recurly.config.RecurlyConfigService;
import com.adyen.commerce.connector.recurly.http.RecurlyHttpClient;
import com.adyen.commerce.connector.recurly.http.RecurlyHttpResponse;

/**
 * httpclient5-based transport. IOExceptions are treated as transient and surfaced as
 * {@link RetryableBillingException}.
 */
public class DefaultRecurlyHttpClient implements RecurlyHttpClient {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultRecurlyHttpClient.class);
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private volatile CloseableHttpClient httpClient;
    private final RecurlyConfigService configService;

    private static final ContentType JSON_UTF8 = ContentType.create("application/json", StandardCharsets.UTF_8);

    public DefaultRecurlyHttpClient(final RecurlyConfigService configService) {
        this.configService = configService;
    }

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
    public RecurlyHttpResponse patch(final String url, final String authorizationHeader, final String acceptHeader,
                                     final String jsonBody, final String idempotencyKey)
            throws RetryableBillingException {
        final HttpPatch request = new HttpPatch(url);
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
        final String operation = operation(request);
        final long startedAt = System.nanoTime();
        try {
            final RecurlyHttpResponse result = getHttpClient().execute(request, response ->
            {
                final String body = response.getEntity() == null
                        ? ""
                        : EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                return new RecurlyHttpResponse(response.getCode(), body);
            });
            final long durationMs = elapsedMillis(startedAt);
            final String outcome = result.isSuccess() ? "success" : "failure";
            final String errorClass = classifyStatus(result.statusCode());
            final boolean retryable = isRetryableStatus(result.statusCode());
            if (result.isSuccess()) {
                LOG.info("event=connector_call platform=RECURLY operation={} method={} outcome={} duration_ms={} "
                                + "http_status={} error_class={} retryable={} idempotency_key_present={} correlation_id={}",
                        operation, request.getMethod(), outcome, durationMs, result.statusCode(), errorClass, retryable,
                        StringUtils.isNotBlank(idempotencyKey), correlationId());
            } else {
                LOG.warn("event=connector_call platform=RECURLY operation={} method={} outcome={} duration_ms={} "
                                + "http_status={} error_class={} retryable={} idempotency_key_present={} correlation_id={}",
                        operation, request.getMethod(), outcome, durationMs, result.statusCode(), errorClass, retryable,
                        StringUtils.isNotBlank(idempotencyKey), correlationId());
            }
            return result;
        } catch (final IOException e) {
            LOG.warn("event=connector_call platform=RECURLY operation={} method={} outcome=failure duration_ms={} "
                            + "http_status=none error_class={} exception_class={} retryable=true "
                            + "idempotency_key_present={} correlation_id={}", operation, request.getMethod(),
                    elapsedMillis(startedAt), classifyException(e), e.getClass().getName(),
                    StringUtils.isNotBlank(idempotencyKey), correlationId());
            throw new RetryableBillingException("Recurly HTTP operation '" + operation + "' failed", e);
        }
    }

    protected String operation(final HttpUriRequestBase request) {
        final String path = StringUtils.defaultString(request.getPath()).toLowerCase(Locale.ROOT);
        if (path.contains("/billing_info")) return "import_token";
        if (path.contains("/transactions/") || path.contains("/invoices/")) return "resolve_webhook";
        if (path.matches(".*/subscriptions/?$") && "POST".equals(request.getMethod())) return "create_subscription";
        if (path.endsWith("/change")) return "update_subscription";
        if (path.contains("/subscriptions/")) return "cancel_subscription";
        if (path.contains("/accounts")) return "ensure_customer";
        return "http_" + request.getMethod().toLowerCase(Locale.ROOT);
    }

    private static String classifyStatus(final int status) {
        if (status >= 200 && status < 300) return "none";
        if (status == 429) return "rate_limit";
        if (status >= 500) return "remote_5xx";
        if (status >= 400) return "remote_4xx";
        return "unexpected_status";
    }

    private static boolean isRetryableStatus(final int status) {
        return status == 408 || status == 409 || status == 429 || status >= 500;
    }

    private static String classifyException(final IOException error) {
        final String type = error.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        return type.contains("timeout") ? "timeout" : "connection";
    }

    private static long elapsedMillis(final long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private static String correlationId() {
        return StringUtils.defaultIfBlank(MDC.get("correlationId"), "none");
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
                            // Without this the wait for a free pooled connection defaults to three
                            // minutes, so the configured timeouts stop being the upper bound a caller
                            // sees: under load a platform worker thread blocks in the lease long before
                            // its request is ever sent.
                            .setConnectionRequestTimeout(
                                    Timeout.ofMilliseconds(configService.getConnectionRequestTimeoutMillis()))
                            .build();
                    // Every call targets the one Recurly host, so the default per-route cap of 2 (and pool
                    // of 5) would serialize the whole platform onto a couple of connections.
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

}
