package com.adyen.commerce.services.impl;

import com.adyen.model.checkout.BillingAddress;
import com.adyen.model.checkout.DeliveryAddress;
import de.hybris.platform.commercefacades.user.data.AddressData;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

/**
 * Utility class for converting Hybris AddressData to Adyen address objects
 */
public class AddressConverter {
    private static final Logger LOG = Logger.getLogger(AddressConverter.class);
    
    private static final String DEFAULT_VALUE = "NA";

    /**
     * Converts AddressData to DeliveryAddress
     */
    public static DeliveryAddress convertToDeliveryAddress(AddressData addressData) {
        if (addressData == null) {
            LOG.warn("Null address data provided for delivery address conversion");
            return null;
        }

        DeliveryAddress address = new DeliveryAddress();
        setDefaultValues(address);
        populateDeliveryAddressFields(address, addressData);
        return address;
    }

    /**
     * Converts AddressData to BillingAddress
     */
    public static BillingAddress convertToBillingAddress(AddressData addressData) {
        if (addressData == null) {
            LOG.warn("Null address data provided for billing address conversion");
            return null;
        }

        BillingAddress address = new BillingAddress();
        setDefaultValues(address);
        populateBillingAddressFields(address, addressData);
        return address;
    }

    private static void setDefaultValues(DeliveryAddress address) {
        address.setCity(DEFAULT_VALUE);
        address.setCountry(DEFAULT_VALUE);
        address.setHouseNumberOrName(DEFAULT_VALUE);
        address.setPostalCode(DEFAULT_VALUE);
        address.setStateOrProvince(DEFAULT_VALUE);
        address.setStreet(DEFAULT_VALUE);
    }

    private static void setDefaultValues(BillingAddress address) {
        address.setCity(DEFAULT_VALUE);
        address.setCountry(DEFAULT_VALUE);
        address.setHouseNumberOrName(DEFAULT_VALUE);
        address.setPostalCode(DEFAULT_VALUE);
        address.setStateOrProvince(DEFAULT_VALUE);
        address.setStreet(DEFAULT_VALUE);
    }

    private static void populateDeliveryAddressFields(DeliveryAddress address, AddressData addressData) {
        setIfNotEmpty(addressData.getTown(), address::setCity);
        setIfNotEmpty(addressData.getLine1(), address::setStreet);
        setIfNotEmpty(addressData.getLine2(), address::setHouseNumberOrName);
        setIfNotEmpty(addressData.getPostalCode(), address::setPostalCode);
        
        if (addressData.getCountry() != null) {
            setIfNotEmpty(addressData.getCountry().getIsocode(), address::setCountry);
        }
        
        setStateOrProvince(addressData, address::setStateOrProvince);
    }

    private static void populateBillingAddressFields(BillingAddress address, AddressData addressData) {
        setIfNotEmpty(addressData.getTown(), address::setCity);
        setIfNotEmpty(addressData.getLine1(), address::setStreet);
        setIfNotEmpty(addressData.getLine2(), address::setHouseNumberOrName);
        setIfNotEmpty(addressData.getPostalCode(), address::setPostalCode);
        
        if (addressData.getCountry() != null) {
            setIfNotEmpty(addressData.getCountry().getIsocode(), address::setCountry);
        }
        
        setStateOrProvince(addressData, address::setStateOrProvince);
    }

    private static void setIfNotEmpty(String value, java.util.function.Consumer<String> setter) {
        if (StringUtils.isNotEmpty(value)) {
            setter.accept(value);
        }
    }

    private static void setStateOrProvince(AddressData addressData, java.util.function.Consumer<String> setter) {
        if (addressData.getRegion() != null) {
            if (StringUtils.isNotEmpty(addressData.getRegion().getIsocodeShort())) {
                setter.accept(addressData.getRegion().getIsocodeShort());
            } else if (StringUtils.isNotEmpty(addressData.getRegion().getIsocode())) {
                setter.accept(addressData.getRegion().getIsocode());
            }
        }
    }

    /**
     * Truncates state/province code for Boleto payments (Brazil specific requirement)
     */
    public static void truncateStateForBoleto(DeliveryAddress address) {
        if (address != null && StringUtils.isNotEmpty(address.getStateOrProvince()) 
            && address.getStateOrProvince().length() > 2) {
            String shortState = address.getStateOrProvince().substring(
                address.getStateOrProvince().length() - 2);
            address.setStateOrProvince(shortState);
        }
    }

    /**
     * Truncates state/province code for Boleto payments (Brazil specific requirement)
     */
    public static void truncateStateForBoleto(BillingAddress address) {
        if (address != null && StringUtils.isNotEmpty(address.getStateOrProvince()) 
            && address.getStateOrProvince().length() > 2) {
            String shortState = address.getStateOrProvince().substring(
                address.getStateOrProvince().length() - 2);
            address.setStateOrProvince(shortState);
        }
    }
}