package com.adyen.commerce.request;

import com.adyen.model.checkout.PayPalDetails;
import de.hybris.platform.commercefacades.user.data.AddressData;

import java.io.Serializable;

public class PayPalIntermediateRequest implements Serializable {
    private String productCode;
    private PayPalDetails payPalDetails;

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
}
