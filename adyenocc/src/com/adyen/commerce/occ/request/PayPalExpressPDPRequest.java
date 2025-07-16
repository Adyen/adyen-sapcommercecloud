package com.adyen.commerce.occ.request;

public class PayPalExpressPDPRequest extends PayPalExpressCartRequest {
    private String productCode;

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }
}
