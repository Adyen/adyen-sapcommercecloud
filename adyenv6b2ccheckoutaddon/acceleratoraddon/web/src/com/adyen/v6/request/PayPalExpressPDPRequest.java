package com.adyen.v6.request;

public class PayPalExpressPDPRequest extends PayPalExpressCartRequest {
    private String cartGuid;

    public String getCartGuid() {
        return cartGuid;
    }

    public void setCartGuid(String cartGuid) {
        this.cartGuid = cartGuid;
    }
}
