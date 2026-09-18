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
     * {@code false}: that would quietly run the no-NTID flow against a site set up for the opposite. Every
     * caller sits in a method declaring {@code BillingException}, so failing fast costs nothing.
     */
    boolean isExternalNtidFeatureEnabled() throws ConnectorNotConfiguredException;

    /**
     * Same reasoning as {@link #isExternalNtidFeatureEnabled()}: {@code false} means "the account's single
     * primary billing info", not "unknown", and three branches in the API client turn on it.
     */
    boolean isWalletEnabled() throws ConnectorNotConfiguredException;


    /**
     * Whether shoppers may repoint a subscription at another payment method the account already holds.
     * Separate from Wallet, which also decides how a token is imported when a subscription is created, so a
     * site enabling Wallet for that reason does not thereby acquire a shopper-facing card change.
     */
    boolean isPaymentMethodChangeEnabledOrFalse();

    /**
     * Whether shoppers may be sent to Recurly's hosted account management page to give Recurly a card.
     * False unless the flag is on AND a hosted-pages host is configured, because the address is assembled
     * from that host and cannot be asked for.
     */
    boolean isHostedAccountManagementEnabledOrFalse();

    /** Host serving this site's Recurly hosted pages, e.g. {@code mystore.recurly.com}. */
    String getHostedPagesHost();

    /**
     * The same question as {@link #isExternalNtidFeatureEnabled()}, answered without throwing, for callers
     * that run while a page renders and must offer nothing rather than fail.
     */
    boolean isExternalNtidFeatureEnabledOrFalse();

    /**
     * Whether to put the network transaction id on the billing-info import itself. Unproven against
     * Recurly, so it is separate from {@link #isExternalNtidFeatureEnabled()} and defaults off.
     */
    boolean isNetworkTransactionIdOnBillingInfoEnabled();
}
