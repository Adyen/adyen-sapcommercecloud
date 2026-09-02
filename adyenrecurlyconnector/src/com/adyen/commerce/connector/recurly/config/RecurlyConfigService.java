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

    /**
     * Selects a mode rather than granting a permission, so "not configured" must not silently read as
     * {@code false}: that would quietly run the no-NTID flow against a site set up for the opposite.
     * Every caller sits in a method declaring {@code BillingException}, so failing fast costs nothing.
     */
    boolean isExternalNtidFeatureEnabled() throws ConnectorNotConfiguredException;

    /**
     * Same reasoning as {@link #isExternalNtidFeatureEnabled()}: {@code false} means "the account's single
     * primary billing info", not "unknown", and three branches in the API client turn on it.
     */
    boolean isWalletEnabled() throws ConnectorNotConfiguredException;
}
