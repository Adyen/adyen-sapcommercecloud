package com.adyen.commerce.connector.recurly.http;

import com.adyen.commerce.connector.exception.ConnectorNotConfiguredException;
import com.adyen.commerce.connector.exception.RetryableBillingException;

public interface RecurlyHttpClient {
    RecurlyHttpResponse get(String path)
            throws RetryableBillingException, ConnectorNotConfiguredException;

    RecurlyHttpResponse post(
            String path,
            String jsonBody,
            String idempotencyKey)
            throws RetryableBillingException, ConnectorNotConfiguredException;
}
