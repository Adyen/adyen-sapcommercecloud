package com.adyen.v6.request;

import java.io.Serializable;

public class PayPalExpressShippingUpdateRequestBase implements Serializable {
    private String cartGuid;
    private String paymentData;
    private String pspReference;

    public String getCartGuid() {
        return cartGuid;
    }

    public void setCartGuid(String cartGuid) {
        this.cartGuid = cartGuid;
    }

    public String getPaymentData() {
        return paymentData;
    }

    public void setPaymentData(String paymentData) {
        this.paymentData = paymentData;
    }

    public String getPspReference() {
        return pspReference;
    }

    public void setPspReference(String pspReference) {
        this.pspReference = pspReference;
    }
}
