package com.adyen.commerce.services.impl;

import com.adyen.model.checkout.ApplicationInfo;
import com.adyen.model.checkout.CommonField;
import com.adyen.model.checkout.ExternalPlatform;
import com.adyen.v6.constants.Adyenv6coreConstants;
import com.adyen.v6.model.RequestInfo;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static com.adyen.v6.constants.Adyenv6coreConstants.*;

public class ApplicationInfoService {
    private static final String PLATFORM_VERSION_PROPERTY = "build.version.api";
    private static final String ADYEN_POM_PROPERTIES = "META-INF/maven/com.adyen/adyen-java-api-library/pom.properties";
    private static final String PROPERTY_NOT_FOUND = "unknown";
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
        applicationInfo.setAdyenLibrary(new CommonField()
                .name("adyen-java-api-library")
                .version(getAdyenJavaApiLibraryVersion()));

        return applicationInfo;
    }

    protected String getAdyenJavaApiLibraryVersion() {
        try (InputStream inputStream = ApplicationInfo.class.getClassLoader().getResourceAsStream(ADYEN_POM_PROPERTIES)) {
            if (inputStream == null) {
                return PROPERTY_NOT_FOUND;
            }

            Properties properties = new Properties();
            properties.load(inputStream);
            return StringUtils.defaultIfBlank(properties.getProperty("version"), PROPERTY_NOT_FOUND);
        } catch (IOException e) {
            return PROPERTY_NOT_FOUND;
        }
    }

    protected String getPlatformVersion() {
        return configurationService.getConfiguration().getString(PLATFORM_VERSION_PROPERTY);
    }

    public void setConfigurationService(ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }
}


