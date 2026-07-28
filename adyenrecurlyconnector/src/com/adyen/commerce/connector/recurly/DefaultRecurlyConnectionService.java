package com.adyen.commerce.connector.recurly;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.adyen.commerce.connector.exception.ConnectorNotConfiguredException;
import com.adyen.commerce.connector.exception.RetryableBillingException;
import com.adyen.commerce.connector.recurly.config.RecurlyConfigService;
import com.adyen.commerce.connector.recurly.http.RecurlyHttpClient;
import com.adyen.commerce.connector.recurly.http.RecurlyHttpResponse;

public class DefaultRecurlyConnectionService implements RecurlyConnectionService
{
    private RecurlyHttpClient recurlyHttpClient;
    private RecurlyConfigService recurlyConfigService;

    @Override
    public boolean testConnection() throws RetryableBillingException, ConnectorNotConfiguredException
    {
        final String auth = "Basic " + Base64.getEncoder()
                .encodeToString((recurlyConfigService.getApiKey() + ":").getBytes(StandardCharsets.UTF_8));
        final String accept = "application/vnd.recurly." + recurlyConfigService.getApiVersion() + "+json";
        final RecurlyHttpResponse response = recurlyHttpClient
                .get(recurlyConfigService.getApiBaseUrl() + "/accounts?limit=1", auth, accept);

        return response.isSuccess();
    }

    public void setRecurlyHttpClient(final RecurlyHttpClient recurlyHttpClient)
    {
        this.recurlyHttpClient = recurlyHttpClient;
    }

    public void setRecurlyConfigService(final RecurlyConfigService recurlyConfigService)
    {
        this.recurlyConfigService = recurlyConfigService;
    }
}
