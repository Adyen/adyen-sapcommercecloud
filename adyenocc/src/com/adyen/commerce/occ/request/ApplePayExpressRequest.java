package com.adyen.commerce.occ.request;

import com.adyen.model.checkout.ApplePayDetails;
import de.hybris.platform.commercefacades.user.data.AddressData;

import java.io.Serializable;

public class ApplePayExpressRequest implements Serializable {
    private AddressData addressData;
    private ApplePayDetails applePayDetails;
    private String cartId;

    public String getCartId() {
        return cartId;
    }

    public void setCartId(String cartId) {
        this.cartId = cartId;
    }

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
