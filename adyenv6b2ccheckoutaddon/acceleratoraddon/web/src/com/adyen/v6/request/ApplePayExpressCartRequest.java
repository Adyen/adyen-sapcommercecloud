package com.adyen.v6.request;

import com.adyen.model.checkout.ApplePayDetails;
import de.hybris.platform.commercefacades.user.data.AddressData;

import java.io.Serializable;

public class ApplePayExpressCartRequest implements Serializable {
    private AddressData addressData;
    private ApplePayDetails applePayDetails;

    public AddressData getAddressData() {
        return addressData;
    }

    public void setAddressData(AddressData addressData) {
        this.addressData = addressData;
    }

    public ApplePayDetails getApplePayDetails() {
        return applePayDetails;
    }

    public void setApplePayDetails(ApplePayDetails applePayDetails) {
        this.applePayDetails = applePayDetails;
    }
}
