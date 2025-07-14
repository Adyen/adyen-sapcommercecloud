package com.adyen.commerce.occ.request;

import com.adyen.model.checkout.PayPalDetails;

import java.io.Serializable;

public class PayPalIntermediateRequest implements Serializable {
    private String productCode;
    private PayPalDetails payPalDetails;
    private String cartId;

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public PayPalDetails getPayPalDetails() {
        return payPalDetails;
    }

    public void setPayPalDetails(PayPalDetails payPalDetails) {
        this.payPalDetails = payPalDetails;
    }

    public String getCartId() {
        return cartId;
    }

    public void setCartId(String cartId) {
        this.cartId = cartId;
    }
}
