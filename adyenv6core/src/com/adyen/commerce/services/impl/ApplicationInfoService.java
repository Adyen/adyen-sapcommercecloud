package com.adyen.commerce.services.impl;

import com.adyen.model.checkout.ApplicationInfo;
import com.adyen.model.checkout.CommonField;
import com.adyen.model.checkout.ExternalPlatform;
import com.adyen.v6.constants.Adyenv6coreConstants;
import com.adyen.v6.model.RequestInfo;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import org.apache.commons.lang3.StringUtils;

import static com.adyen.v6.constants.Adyenv6coreConstants.*;

public class ApplicationInfoService {
    private static final String PLATFORM_VERSION_PROPERTY = "build.version.api";

    private ConfigurationService configurationService;


    public ApplicationInfo createApplicationInfo(RequestInfo requestInfo) {
        ApplicationInfo applicationInfo = new ApplicationInfo();

        CommonField version = new CommonField()
                .name(String.format("%s [%s]", PLUGIN_NAME, requestInfo.getStorefrontType().getValue()))
                .version(StringUtils.isNotEmpty(requestInfo.getStorefrontVersion()) ?
                        String.format("%s [%s]", PLUGIN_VERSION, requestInfo.getStorefrontVersion()) : PLUGIN_VERSION);

        ExternalPlatform externalPlatform = new ExternalPlatform()
                .name(PLATFORM_NAME)
                .version(getPlatformVersion())
                .integrator(Adyenv6coreConstants.INTEGRATOR);

        applicationInfo.setExternalPlatform(externalPlatform);
        applicationInfo.setMerchantApplication(version);
        applicationInfo.setAdyenPaymentSource(version);

        return applicationInfo;
    }

    protected String getPlatformVersion() {
        return configurationService.getConfiguration().getString(PLATFORM_VERSION_PROPERTY);
    }

    public void setConfigurationService(ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }
}


