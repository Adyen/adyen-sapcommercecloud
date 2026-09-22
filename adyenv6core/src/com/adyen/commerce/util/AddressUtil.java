package com.adyen.commerce.util;

import de.hybris.platform.commercefacades.user.data.AddressData;

import java.util.Optional;

public class AddressUtil {

    public static String getCountryCode(AddressData billingAddress, AddressData deliveryAddress) {
        return Optional.ofNullable(billingAddress)
                .or(() -> Optional.ofNullable(deliveryAddress))
                .map(AddressData::getCountry)
                .map(country -> country.getIsocode())
                .orElse("");
    }

}
