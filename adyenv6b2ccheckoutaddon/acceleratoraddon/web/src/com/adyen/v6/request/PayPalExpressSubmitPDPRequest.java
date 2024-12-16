package com.adyen.v6.request;

import com.adyen.model.checkout.PayPalDetails;

import java.io.Serializable;

public class PayPalExpressSubmitPDPRequest implements Serializable {
    private PayPalDetails payPalDetails;
    private String productCode;

    public PayPalDetails getPayPalDetails() {
        return payPalDetails;
    }

    public void setPayPalDetails(PayPalDetails payPalDetails) {
        this.payPalDetails = payPalDetails;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }
}