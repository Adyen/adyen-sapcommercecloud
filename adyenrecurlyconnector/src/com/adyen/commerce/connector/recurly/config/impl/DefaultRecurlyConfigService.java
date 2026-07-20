package com.adyen.commerce.connector.recurly.config.impl;

import org.apache.commons.lang3.StringUtils;

import com.adyen.commerce.connector.exception.ConnectorNotConfiguredException;
import com.adyen.commerce.connector.recurly.config.RecurlyConfigService;

import de.hybris.platform.servicelayer.config.ConfigurationService;

public class DefaultRecurlyConfigService
        implements RecurlyConfigService
{
    private static final String P_API_KEY = "recurly.apiKey";
    private static final String P_BASE_URL = "recurly.baseUrl";

    private ConfigurationService configurationService;

    @Override
    public String getApiKey() throws ConnectorNotConfiguredException {
        return required(P_API_KEY);
    }

    @Override
    public String getBaseUrl() throws ConnectorNotConfiguredException {
        return StringUtils.removeEnd(required(P_BASE_URL), "/");
    }

    protected String required(final String key) throws ConnectorNotConfiguredException {
        final String value = StringUtils.trimToNull(
                configurationService
                        .getConfiguration()
                        .getString(key, null));

        if (value == null)
        {
            throw new ConnectorNotConfiguredException(
                    "Missing Recurly configuration property '"
                            + key + "'");
        }

        return value;
    }

    public void setConfigurationService(
            final ConfigurationService configurationService)
    {
        this.configurationService = configurationService;
    }
}
