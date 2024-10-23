package com.adyen.v6.request;

import java.io.Serializable;

public class GooglePayExpressPDPRequest extends GooglePayExpressCartRequest {
    private String productCode;

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }
}
