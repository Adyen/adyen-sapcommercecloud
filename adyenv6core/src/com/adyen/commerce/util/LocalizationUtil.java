package com.adyen.commerce.util;

import de.hybris.platform.util.localization.Localization;
import org.springframework.util.Assert;

public class LocalizationUtil {

    private LocalizationUtil() {
        // Private constructor to prevent instantiation
    }

    public static String getLocalizedStringOrDefault(final String key, final String defaultValue) {
        Assert.notNull(key, "key must not be null");
        Assert.notNull(defaultValue, "defaultValue must not be null");

        String localizedString = Localization.getLocalizedString(key);

        if (key.equals(localizedString)) {
            return defaultValue;
        }
        return localizedString;
    }

}
