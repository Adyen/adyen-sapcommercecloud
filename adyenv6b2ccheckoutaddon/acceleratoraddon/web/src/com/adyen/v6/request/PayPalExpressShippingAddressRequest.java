package com.adyen.v6.request;

import de.hybris.platform.commercefacades.user.data.AddressData;

import java.io.Serializable;

public class PayPalExpressShippingAddressRequest extends PayPalExpressShippingUpdateRequestBase implements Serializable {
    private AddressData addressData;

    public AddressData getAddressData() {
        return addressData;
    }

    public void setAddressData(AddressData addressData) {
        this.addressData = addressData;
    }
}
