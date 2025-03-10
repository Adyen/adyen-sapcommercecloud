package com.adyen.v6.request;

import java.io.Serializable;

public class PayPalExpressShippingMethodRequest extends PayPalExpressShippingUpdateRequestBase implements Serializable {
    private String shippingMethodCode;

    public String getShippingMethodCode() {
        return shippingMethodCode;
    }

    public void setShippingMethodCode(String shippingMethodCode) {
        this.shippingMethodCode = shippingMethodCode;
    }
}
