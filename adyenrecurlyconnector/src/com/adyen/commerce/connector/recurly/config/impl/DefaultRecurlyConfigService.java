package com.adyen.commerce.connector.recurly.config.impl;

import org.apache.commons.lang3.StringUtils;

import com.adyen.commerce.connector.exception.ConnectorNotConfiguredException;
import com.adyen.commerce.connector.recurly.config.RecurlyConfigService;

import de.hybris.platform.servicelayer.config.ConfigurationService;

/**
 * Reads Recurly configuration from the platform {@link ConfigurationService} (project/local.properties).
 */
public class DefaultRecurlyConfigService implements RecurlyConfigService
{
    static final String P_API_KEY = "recurly.apiKey";
    static final String P_BASE_URL = "recurly.baseUrl";
    static final String P_API_VERSION = "recurly.apiVersion";
    static final String P_GATEWAY_CODE = "recurly.gatewayCode";
    static final String P_MERCHANT = "recurly.adyenMerchantAccount";
    static final String P_MINIMUM_START_DELAY_SECONDS = "recurly.minimumStartDelaySeconds";
    static final String P_CONNECT_TIMEOUT_MILLIS = "recurly.http.connectTimeoutMillis";
    static final String P_RESPONSE_TIMEOUT_MILLIS = "recurly.http.responseTimeoutMillis";
    static final String P_WEBHOOK_SIGNING_KEY = "recurly.webhookSigningKey";
    static final String P_WEBHOOK_TOLERANCE_SECONDS = "recurly.webhookToleranceSeconds";
    static final String DEFAULT_API_VERSION = "v2021-02-25";
    static final int DEFAULT_MINIMUM_START_DELAY_SECONDS = 300;
    static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 5000;
    static final int DEFAULT_RESPONSE_TIMEOUT_MILLIS = 30000;
    static final int DEFAULT_WEBHOOK_TOLERANCE_SECONDS = 300;

    private final ConfigurationService configurationService;

    public DefaultRecurlyConfigService(final ConfigurationService configurationService)
    {
        this.configurationService = configurationService;
    }

    @Override
    public String getApiKey() throws ConnectorNotConfiguredException
    {
        return required(P_API_KEY);
    }

    @Override
    public String getApiBaseUrl() throws ConnectorNotConfiguredException
    {
        return StringUtils.removeEnd(required(P_BASE_URL), "/");
    }

    @Override
    public String getApiVersion()
    {
        return StringUtils.defaultIfBlank(optional(P_API_VERSION), DEFAULT_API_VERSION);
    }

    @Override
    public String getGatewayCode() throws ConnectorNotConfiguredException
    {
        return required(P_GATEWAY_CODE);
    }

    @Override
    public String getConfiguredAdyenMerchantAccount()
    {
        return optional(P_MERCHANT);
    }

    @Override
    public int getMinimumStartDelaySeconds()
    {
        return positiveInt(P_MINIMUM_START_DELAY_SECONDS, DEFAULT_MINIMUM_START_DELAY_SECONDS);
    }

    @Override
    public int getConnectTimeoutMillis()
    {
        return positiveInt(P_CONNECT_TIMEOUT_MILLIS, DEFAULT_CONNECT_TIMEOUT_MILLIS);
    }

    @Override
    public int getResponseTimeoutMillis()
    {
        return positiveInt(P_RESPONSE_TIMEOUT_MILLIS, DEFAULT_RESPONSE_TIMEOUT_MILLIS);
    }

    @Override
    public String getWebhookSigningKey() throws ConnectorNotConfiguredException
    {
        return required(P_WEBHOOK_SIGNING_KEY);
    }

    @Override
    public int getWebhookToleranceSeconds()
    {
        return positiveInt(P_WEBHOOK_TOLERANCE_SECONDS, DEFAULT_WEBHOOK_TOLERANCE_SECONDS);
    }

    protected String required(final String key) throws ConnectorNotConfiguredException
    {
        final String value = optional(key);
        if (value == null)
        {
            throw new ConnectorNotConfiguredException("Missing Recurly configuration property '" + key + "'");
        }
        return value;
    }

    protected String optional(final String key)
    {
        return StringUtils.trimToNull(configurationService.getConfiguration().getString(key, null));
    }

    protected int positiveInt(final String key, final int defaultValue)
    {
        final int value = configurationService.getConfiguration().getInt(key, defaultValue);
        return value > 0 ? value : defaultValue;
    }
}
