package com.adyen.commerce.connector.recurly.http;

import com.adyen.commerce.connector.exception.RetryableBillingException;

public interface RecurlyHttpClient {
    RecurlyHttpResponse get(String url, String authorizationHeader, String acceptHeader)
            throws RetryableBillingException;

    RecurlyHttpResponse post(String url, String authorizationHeader, String acceptHeader, String jsonBody,
                             String idempotencyKey) throws RetryableBillingException;

    RecurlyHttpResponse patch(String url, String authorizationHeader, String acceptHeader, String jsonBody,
                              String idempotencyKey) throws RetryableBillingException;

    RecurlyHttpResponse put(String url, String authorizationHeader, String acceptHeader, String jsonBody,
                            String idempotencyKey) throws RetryableBillingException;

    RecurlyHttpResponse delete(String url, String authorizationHeader, String acceptHeader, String idempotencyKey)
            throws RetryableBillingException;
}
