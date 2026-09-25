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

    int getConnectionRequestTimeoutMillis();

    /** Pool size, total and per route: every call goes to the one Recurly host. */
    int getMaxConnections();

    String getWebhookSigningKey() throws ConnectorNotConfiguredException;

    int getWebhookToleranceSeconds();

    /** Throws when unset: this selects a flow, so "not configured" must not read as {@code false}. */
    boolean isExternalNtidFeatureEnabled() throws ConnectorNotConfiguredException;

    /** Throws when unset: {@code false} means "the account's single primary billing info", not "unknown". */
    boolean isWalletEnabled() throws ConnectorNotConfiguredException;

    /** Whether shoppers may repoint a subscription at another payment method the account already holds. */
    boolean isPaymentMethodChangeEnabledOrFalse();

    /** False unless the flag is on and a hosted-pages host is configured, since the page URL is built from it. */
    boolean isHostedAccountManagementEnabledOrFalse();

    /** Host serving this site's Recurly hosted pages, e.g. {@code mystore.recurly.com}. */
    String getHostedPagesHost();

    /** {@link #isExternalNtidFeatureEnabled()} without throwing, for page rendering. */
    boolean isExternalNtidFeatureEnabledOrFalse();

    /** Whether a card chosen for a subscription also becomes the account's primary billing info. */
    boolean isPromoteChosenCardToPrimaryEnabled();

    /** Whether the billing-info import carries the network transaction id. Unproven against Recurly; off by default. */
    boolean isNetworkTransactionIdOnBillingInfoEnabled();
}
