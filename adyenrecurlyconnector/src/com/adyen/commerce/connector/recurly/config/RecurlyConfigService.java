package com.adyen.commerce.connector.recurly.config;

import com.adyen.commerce.connector.exception.ConnectorNotConfiguredException;

public interface RecurlyConfigService {
    String getApiKey() throws ConnectorNotConfiguredException;

    String getApiBaseUrl() throws ConnectorNotConfiguredException;

    String getApiVersion();

    String getGatewayCode() throws ConnectorNotConfiguredException;

    String getConfiguredAdyenMerchantAccount();

    int getMinimumStartDelaySeconds();

    int getConnectTimeoutMillis();

    int getResponseTimeoutMillis();

    /** How long a caller may wait for a free pooled connection before failing. */
    int getConnectionRequestTimeoutMillis();

    /** Size of the connection pool, total and per route — every call goes to the one Recurly host. */
    int getMaxConnections();

    String getWebhookSigningKey() throws ConnectorNotConfiguredException;

    int getWebhookToleranceSeconds();

    boolean isExternalNtidFeatureEnabled();

    boolean isWalletEnabled();
}
