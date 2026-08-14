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
 * Credentials and feature flags come from the current base store's {@code recurlyConfig} (Backoffice:
 * Adyen Configuration &gt; Recurly Config). Only
 * the transport tuning that is not per-store — API version, timeouts, pool size, webhook tolerance —
 * still comes from the platform {@link ConfigurationService} (project/local.properties).
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

    private final ConfigurationService configurationService;
    private final BaseStoreService baseStoreService;

    public DefaultRecurlyConfigService(final ConfigurationService configurationService, BaseStoreService baseStoreService) {
        this.configurationService = configurationService;
        this.baseStoreService = baseStoreService;
    }

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
     * Read off the Recurly configuration, not off the base store. The gateway-binding guard compares this against the
     * store's own Adyen merchant account, so taking it from the store would compare a value with itself
     * and could never fail.
     *
     * <p>Cannot signal "not configured" by throwing: {@link RecurlyConfigService} and the
     * {@code SubscriptionBillingConnector} SPI both declare this without a checked exception. {@code null}
     * is not read as an exemption, though — {@code DefaultConnectorMerchantAccountValidator} exempts only
     * ADYEN_NATIVE and rejects a blank answer from an external connector, so the unconfigured case fails
     * closed before activation rather than at token import.</p>
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

    protected BaseStoreModel getCurrentBaseStore() {
        return baseStoreService.getCurrentBaseStore();
    }

    /**
     * The same lookup as {@link #requireRecurlyConfig()}, reported as {@code null} instead of thrown.
     * Deliberately delegates rather than repeating the checks: the two must agree on exactly when a
     * store counts as configured, and the only caller — {@link #getConfiguredAdyenMerchantAccount()} —
     * is one the SPI forbids from throwing.
     */
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
                throw invalidBaseUrl(baseUrl);
            }
        } catch (final URISyntaxException e) {
            throw invalidBaseUrl(baseUrl);
        }
    }

    protected ConnectorNotConfiguredException invalidBaseUrl(final String baseUrl) {
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
     * Having Recurly configuration is the condition; being the store's active platform is not. The
     * deleted {@code adyenSubscriptionPlatform} attribute was checked here, and that check was
     * deliberately NOT carried over to its replacement {@code activeBillingPlatform}, for two reasons.
     *
     * <p>It would be redundant where it fires and harmful where it does not. On activation the connector
     * is already chosen by {@code getActiveConnector(store)} — reading the same attribute again a line
     * later cannot discover anything. And a refusal here surfaces as {@code null} from
     * {@link #getConfiguredAdyenMerchantAccount()}, which {@code DefaultConnectorMerchantAccountValidator}
     * reads as "the check does not apply" and skips: the gate would turn a merchant-account mismatch into an
     * unchecked one.</p>
     *
     * <p>Secondly, cancellation routes on {@code subscription.getPlatform()}, so a store that has since
     * migrated must still reach this configuration to cancel what it created on Recurly — the
     * multi-platform coexistence adyensubscriptionconnector-items.xml is built for.</p>
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
}
