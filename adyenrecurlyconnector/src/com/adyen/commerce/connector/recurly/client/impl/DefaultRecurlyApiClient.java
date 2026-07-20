package com.adyen.commerce.connector.recurly.client.impl;

import com.adyen.commerce.connector.exception.ConnectorNotConfiguredException;
import com.adyen.commerce.connector.exception.RetryableBillingException;
import com.adyen.commerce.connector.recurly.client.RecurlyApiClient;
import com.adyen.commerce.connector.recurly.dto.RecurlyAccountCreateRequest;
import com.adyen.commerce.connector.recurly.dto.RecurlyAccountResponse;
import com.adyen.commerce.connector.recurly.dto.RecurlySubscriptionCreateRequest;
import com.adyen.commerce.connector.recurly.dto.RecurlySubscriptionResponse;
import com.adyen.commerce.connector.recurly.http.RecurlyHttpClient;
import com.adyen.commerce.connector.recurly.http.RecurlyHttpResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public class DefaultRecurlyApiClient implements RecurlyApiClient
{
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(
                    DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                    false);

    private RecurlyHttpClient recurlyHttpClient;

    @Override
    public RecurlyAccountResponse createAccount(
            final RecurlyAccountCreateRequest request,
            final String idempotencyKey)
            throws RetryableBillingException, ConnectorNotConfiguredException
    {
        return executePost(
                "/accounts",
                request,
                idempotencyKey,
                RecurlyAccountResponse.class);
    }

    @Override
    public RecurlySubscriptionResponse createSubscription(
            final RecurlySubscriptionCreateRequest request,
            final String idempotencyKey)
            throws RetryableBillingException, ConnectorNotConfiguredException
    {
        return executePost(
                "/subscriptions",
                request,
                idempotencyKey,
                RecurlySubscriptionResponse.class);
    }

    protected <T> T executePost(
            final String path,
            final Object request,
            final String idempotencyKey,
            final Class<T> responseType)
            throws RetryableBillingException, ConnectorNotConfiguredException
    {
        final RecurlyHttpResponse response = recurlyHttpClient.post(
                path,
                writeJson(request),
                idempotencyKey);

        return parseResponse(response, responseType);
    }

    protected String writeJson(final Object request)
    {
        try
        {
            return objectMapper.writeValueAsString(request);
        }
        catch (final JsonProcessingException exception)
        {
            throw new IllegalArgumentException(
                    "Could not serialize Recurly request",
                    exception);
        }
    }

    protected <T> T parseResponse(
            final RecurlyHttpResponse response,
            final Class<T> responseType)
            throws RetryableBillingException
    {
        final int statusCode = response.getStatusCode();

        if (isSuccessful(statusCode))
        {
            return readResponseBody(response, responseType);
        }

        if (isRetryable(statusCode))
        {
            throw new RetryableBillingException(
                    "Temporary Recurly error. HTTP status: "
                            + statusCode);
        }

        throw new RecurlyApiException(
                statusCode,
                response.getBody());
    }

    protected <T> T readResponseBody(
            final RecurlyHttpResponse response,
            final Class<T> responseType)
    {
        try
        {
            return objectMapper.readValue(
                    response.getBody(),
                    responseType);
        }
        catch (final JsonProcessingException exception)
        {
            throw new IllegalStateException(
                    "Could not deserialize successful Recurly response. "
                            + "HTTP status: "
                            + response.getStatusCode(),
                    exception);
        }
    }

    protected boolean isSuccessful(final int statusCode)
    {
        return statusCode >= 200 && statusCode < 300;
    }

    protected boolean isRetryable(final int statusCode)
    {
        return statusCode == 408
                || statusCode == 429
                || statusCode >= 500;
    }

    public void setRecurlyHttpClient(
            final RecurlyHttpClient recurlyHttpClient)
    {
        this.recurlyHttpClient = recurlyHttpClient;
    }
}