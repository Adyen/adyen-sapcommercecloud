package com.adyen.commerce.connector.recurly.config.impl;

import com.adyen.commerce.connector.exception.ConnectorNotConfiguredException;
import com.adyen.commerce.connector.recurly.config.RecurlyConfigService;
import com.adyen.v6.model.RecurlyConfigModel;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Credentials and feature flags come from the current base store's {@code recurlyConfig}; transport tuning
 * (API version, timeouts, pool size, webhook tolerance) comes from {@link ConfigurationService}.
 */
public class DefaultRecurlyConfigService implements RecurlyConfigService {
    static final String P_API_VERSION = "recurly.apiVersion";
    static final String P_MINIMUM_START_DELAY_SECONDS = "recurly.minimumStartDelaySeconds";
    static final String P_CONNECT_TIMEOUT_MILLIS = "recurly.http.connectTimeoutMillis";
    static final String P_RESPONSE_TIMEOUT_MILLIS = "recurly.http.responseTimeoutMillis";
    static final String P_CONNECTION_REQUEST_TIMEOUT_MILLIS = "recurly.http.connectionRequestTimeoutMillis";
    static final String P_MAX_CONNECTIONS = "recurly.http.maxConnections";
    static final String P_WEBHOOK_TOLERANCE_SECONDS = "recurly.webhookToleranceSeconds";
    static final String DEFAULT_API_VERSION = "v2021-02-25";
    static final int DEFAULT_MINIMUM_START_DELAY_SECONDS = 300;
    static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 5000;
    static final int DEFAULT_RESPONSE_TIMEOUT_MILLIS = 30000;
    static final int DEFAULT_CONNECTION_REQUEST_TIMEOUT_MILLIS = 5000;
    static final int DEFAULT_MAX_CONNECTIONS = 20;
    static final int DEFAULT_WEBHOOK_TOLERANCE_SECONDS = 300;

    private ConfigurationService configurationService;
    private BaseStoreService baseStoreService;

    @Override
    public String getApiKey() throws ConnectorNotConfiguredException {
        return required(requireRecurlyConfig().getSubscriptionApiKey(), "subscriptionApiKey");
    }

    @Override
    public String getApiBaseUrl() throws ConnectorNotConfiguredException {
        final String baseUrl = StringUtils.removeEnd(
                required(requireRecurlyConfig().getSubscriptionSiteId(), "subscriptionSiteId"), "/");
        validateBaseUrl(baseUrl);
        return baseUrl;
    }

    @Override
    public String getApiVersion() {
        return StringUtils.defaultIfBlank(optional(P_API_VERSION), DEFAULT_API_VERSION);
    }

    @Override
    public String getGatewayCode() throws ConnectorNotConfiguredException {
        return required(requireRecurlyConfig().getSubscriptionGatewayAccountId(), "subscriptionGatewayAccountId");
    }

    /**
     * Read from the Recurly configuration, not the store: the merchant-account guard compares it with the
     * store's own account. {@code null} when unconfigured, which the guard rejects.
     */
    @Override
    public String getConfiguredAdyenMerchantAccount() {
        final RecurlyConfigModel config = findRecurlyConfig();
        return config == null ? null : StringUtils.trimToNull(config.getAdyenGatewayMerchantAccount());
    }

    @Override
    public int getMinimumStartDelaySeconds() {
        return positiveInt(P_MINIMUM_START_DELAY_SECONDS, DEFAULT_MINIMUM_START_DELAY_SECONDS);
    }

    @Override
    public int getConnectTimeoutMillis() {
        return positiveInt(P_CONNECT_TIMEOUT_MILLIS, DEFAULT_CONNECT_TIMEOUT_MILLIS);
    }

    @Override
    public int getResponseTimeoutMillis() {
        return positiveInt(P_RESPONSE_TIMEOUT_MILLIS, DEFAULT_RESPONSE_TIMEOUT_MILLIS);
    }

    @Override
    public int getConnectionRequestTimeoutMillis() {
        return positiveInt(P_CONNECTION_REQUEST_TIMEOUT_MILLIS, DEFAULT_CONNECTION_REQUEST_TIMEOUT_MILLIS);
    }

    @Override
    public int getMaxConnections() {
        return positiveInt(P_MAX_CONNECTIONS, DEFAULT_MAX_CONNECTIONS);
    }

    @Override
    public String getWebhookSigningKey() throws ConnectorNotConfiguredException {
        return required(requireRecurlyConfig().getRecurlyWebhookSigningKey(), "recurlyWebhookSigningKey");
    }

    @Override
    public int getWebhookToleranceSeconds() {
        return positiveInt(P_WEBHOOK_TOLERANCE_SECONDS, DEFAULT_WEBHOOK_TOLERANCE_SECONDS);
    }

    @Override
    public boolean isExternalNtidFeatureEnabled() throws ConnectorNotConfiguredException {
        return Boolean.TRUE.equals(requireRecurlyConfig().getExternalNtidFeatureEnabled());
    }

    @Override
    public boolean isWalletEnabled() throws ConnectorNotConfiguredException {
        return Boolean.TRUE.equals(requireRecurlyConfig().getWalletEnabled());
    }

    @Override
    public boolean isPaymentMethodChangeEnabledOrFalse() {
        final RecurlyConfigModel config = findRecurlyConfig();
        // Without Wallet the account has a single billing info, so there is nothing to repoint to.
        return config != null
                && Boolean.TRUE.equals(config.getWalletEnabled())
                && Boolean.TRUE.equals(config.getPaymentMethodChangeEnabled());
    }

    @Override
    public boolean isHostedAccountManagementEnabledOrFalse() {
        final RecurlyConfigModel config = findRecurlyConfig();
        // Recurly has no endpoint returning the hosted-page address, so it is built from this host.
        return config != null
                && Boolean.TRUE.equals(config.getHostedAccountManagementEnabled())
                && StringUtils.isNotBlank(config.getHostedPagesHost());
    }

    @Override
    public String getHostedPagesHost() {
        final RecurlyConfigModel config = findRecurlyConfig();
        return config == null ? null : config.getHostedPagesHost();
    }

    @Override
    public boolean isExternalNtidFeatureEnabledOrFalse() {
        final RecurlyConfigModel config = findRecurlyConfig();
        return config != null && Boolean.TRUE.equals(config.getExternalNtidFeatureEnabled());
    }

    @Override
    public boolean isPromoteChosenCardToPrimaryEnabled() {
        final RecurlyConfigModel config = findRecurlyConfig();
        return config != null && Boolean.TRUE.equals(config.getPromoteChosenCardToPrimary());
    }

    @Override
    public boolean isNetworkTransactionIdOnBillingInfoEnabled() {
        final RecurlyConfigModel config = findRecurlyConfig();
        return config != null
                && Boolean.TRUE.equals(config.getExternalNtidFeatureEnabled())
                && Boolean.TRUE.equals(config.getNetworkTransactionIdOnBillingInfoEnabled());
    }

    protected BaseStoreModel getCurrentBaseStore() {
        return baseStoreService.getCurrentBaseStore();
    }

    /** {@link #requireRecurlyConfig()} returning {@code null} instead of throwing. */
    protected RecurlyConfigModel findRecurlyConfig() {
        try {
            return requireRecurlyConfig();
        } catch (final ConnectorNotConfiguredException e) {
            return null;
        }
    }

    protected String required(final String value, final String attributeName) throws ConnectorNotConfiguredException {
        final String normalizedValue = StringUtils.trimToNull(value);

        if (normalizedValue == null) {
            throw new ConnectorNotConfiguredException(
                    "Missing Recurly configuration attribute '" + attributeName + "'");
        }

        return normalizedValue;
    }

    protected void validateBaseUrl(final String baseUrl) throws ConnectorNotConfiguredException {
        try {
            final URI uri = new URI(baseUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || StringUtils.isBlank(uri.getHost())) {
                throw invalidBaseUrl();
            }
        } catch (final URISyntaxException e) {
            throw invalidBaseUrl();
        }
    }

    /** The value is not echoed: it is free text from Backoffice and may hold a mistakenly pasted secret. */
    protected ConnectorNotConfiguredException invalidBaseUrl() {
        return new ConnectorNotConfiguredException("Invalid Recurly API base URL");
    }

    protected String optional(final String key) {
        return StringUtils.trimToNull(configurationService.getConfiguration().getString(key, null));
    }

    protected int positiveInt(final String key, final int defaultValue) {
        final int value = configurationService.getConfiguration().getInt(key, defaultValue);
        return value > 0 ? value : defaultValue;
    }

    /**
     * Does not check {@code activeBillingPlatform}: a store that migrated away must still reach this
     * configuration to cancel what it created on Recurly.
     */
    protected RecurlyConfigModel requireRecurlyConfig()
            throws ConnectorNotConfiguredException {
        final BaseStoreModel baseStore = getCurrentBaseStore();

        if (baseStore == null) {
            throw new ConnectorNotConfiguredException(
                    "No current base store");
        }

        final RecurlyConfigModel config = baseStore.getRecurlyConfig();

        if (config == null) {
            throw new ConnectorNotConfiguredException(
                    "Recurly configuration is missing for base store '" + baseStore.getUid() + "'");
        }

        return config;
    }

    public void setConfigurationService(final ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    public void setBaseStoreService(final BaseStoreService baseStoreService) {
        this.baseStoreService = baseStoreService;
    }
}
