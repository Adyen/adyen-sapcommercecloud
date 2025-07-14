package com.adyen.commerce.occ.request;

import com.adyen.model.checkout.PayPalDetails;
import de.hybris.platform.commercefacades.user.data.AddressData;

import java.io.Serializable;

public class PayPalExpressCartRequest implements Serializable {
    private PayPalDetails payPalDetails;
    private AddressData addressData;
    private String cartId;
    private String returnUrl;

    public PayPalDetails getPayPalDetails() {
        return payPalDetails;
    }

    public void setPayPalDetails(PayPalDetails payPalDetails) {
        this.payPalDetails = payPalDetails;
    }

    public AddressData getAddressData() {
        return addressData;
    }

    public void setAddressData(AddressData addressData) {
        this.addressData = addressData;
    }

    public String getCartId() {
        return cartId;
    }

    public void setCartId(String cartId) {
        this.cartId = cartId;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public void setReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
    }
}
