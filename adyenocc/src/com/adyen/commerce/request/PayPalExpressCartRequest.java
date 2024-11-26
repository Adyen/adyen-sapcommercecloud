package com.adyen.commerce.request;

import com.adyen.model.checkout.PayPalDetails;
import de.hybris.platform.commercefacades.user.data.AddressData;

import java.io.Serializable;

public class PayPalExpressCartRequest implements Serializable {
    private PayPalDetails payPalDetails;
    private AddressData addressData;

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
}
