package com.adyen.commerce.connector.recurly.config;

import com.adyen.commerce.connector.exception.ConnectorNotConfiguredException;

public interface RecurlyConfigService
{
    String getApiKey() throws ConnectorNotConfiguredException;

    String getApiBaseUrl() throws ConnectorNotConfiguredException;

    String getApiVersion();

    String getGatewayCode() throws ConnectorNotConfiguredException;

    String getConfiguredAdyenMerchantAccount();

    int getMinimumStartDelaySeconds();

    int getConnectTimeoutMillis();

    int getResponseTimeoutMillis();

    String getWebhookSigningKey() throws ConnectorNotConfiguredException;

    int getWebhookToleranceSeconds();

    boolean isExternalNtidFeatureEnabled();

    boolean isWalletEnabled();
}
