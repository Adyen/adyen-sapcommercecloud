package com.adyen.commerce.request;

import com.adyen.model.checkout.PaymentRequest;
import com.adyen.v6.forms.AddressForm;


public class PlaceOrderRequest {

    private PaymentRequest paymentRequest;

    private boolean useAdyenDeliveryAddress;
    private AddressForm billingAddress;

    public PaymentRequest getPaymentRequest() {
        return paymentRequest;
    }

    public void setPaymentRequest(PaymentRequest paymentRequest) {
        this.paymentRequest = paymentRequest;
    }


    public boolean isUseAdyenDeliveryAddress() {
        return useAdyenDeliveryAddress;
    }

    public void setUseAdyenDeliveryAddress(boolean useAdyenDeliveryAddress) {
        this.useAdyenDeliveryAddress = useAdyenDeliveryAddress;
    }

    public AddressForm getBillingAddress() {
        return billingAddress;
    }

    public void setBillingAddress(AddressForm billingAddress) {
        this.billingAddress = billingAddress;
    }
}
