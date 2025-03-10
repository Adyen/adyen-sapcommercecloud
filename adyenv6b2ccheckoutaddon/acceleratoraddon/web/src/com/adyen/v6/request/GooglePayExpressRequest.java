package com.adyen.v6.request;

import com.adyen.model.checkout.GooglePayDetails;
import de.hybris.platform.commercefacades.user.data.AddressData;

import java.io.Serializable;

public class GooglePayExpressRequest implements Serializable {
    private GooglePayDetails googlePayDetails;
    private AddressData addressData;
    private String cartId;

    public String getCartId() {
        return cartId;
    }

    public void setCartId(String cartId) {
        this.cartId = cartId;
    }

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
