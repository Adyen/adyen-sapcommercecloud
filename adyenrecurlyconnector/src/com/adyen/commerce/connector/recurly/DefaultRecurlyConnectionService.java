package com.adyen.commerce.connector.recurly;

import com.adyen.commerce.connector.exception.ConnectorNotConfiguredException;
import com.adyen.commerce.connector.exception.RetryableBillingException;
import com.adyen.commerce.connector.recurly.http.RecurlyHttpClient;
import com.adyen.commerce.connector.recurly.http.RecurlyHttpResponse;

public class DefaultRecurlyConnectionService
        implements RecurlyConnectionService
{
    private RecurlyHttpClient recurlyHttpClient;

    @Override
    public boolean testConnection() throws RetryableBillingException, ConnectorNotConfiguredException {
        final RecurlyHttpResponse response =
                recurlyHttpClient.get("/accounts?limit=1");

        return response.isSuccessful();
    }

    public void setRecurlyHttpClient(
            final RecurlyHttpClient recurlyHttpClient)
    {
        this.recurlyHttpClient = recurlyHttpClient;
    }
}