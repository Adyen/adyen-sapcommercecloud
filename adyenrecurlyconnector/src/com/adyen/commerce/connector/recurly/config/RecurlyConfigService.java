package com.adyen.commerce.connector.recurly.config;

import com.adyen.commerce.connector.exception.ConnectorNotConfiguredException;

public interface RecurlyConfigService {
    String getApiKey() throws ConnectorNotConfiguredException;

    String getBaseUrl() throws ConnectorNotConfiguredException;
}
