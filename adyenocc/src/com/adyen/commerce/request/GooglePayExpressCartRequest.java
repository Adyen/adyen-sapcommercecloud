package com.adyen.commerce.request;

import com.adyen.model.checkout.GooglePayDetails;
import de.hybris.platform.commercefacades.user.data.AddressData;

import java.io.Serializable;

public class GooglePayExpressCartRequest implements Serializable {
    private GooglePayDetails googlePayDetails;
    private AddressData addressData;

    public GooglePayDetails getGooglePayDetails() {
        return googlePayDetails;
    }

    public void setGooglePayDetails(GooglePayDetails googlePayDetails) {
        this.googlePayDetails = googlePayDetails;
    }

    public AddressData getAddressData() {
        return addressData;
    }

    public void setAddressData(AddressData addressData) {
        this.addressData = addressData;
    }
}
