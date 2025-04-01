package com.adyen.commerce.utils;

import de.hybris.platform.servicelayer.config.ConfigurationService;

public class WebServicesBaseUrlResolver {
    private ConfigurationService configurationService;


    public String getOCCBaseUrl(final boolean isSecure) {
        String baseUrlKey = "webroot.commercewebservices." + (isSecure ? "https" : "http");
        return configurationService.getConfiguration().getString(baseUrlKey, "");
    }

    public void setConfigurationService(ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }
}
