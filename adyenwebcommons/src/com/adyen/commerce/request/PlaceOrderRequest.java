package com.adyen.commerce.request;

import com.adyen.model.checkout.PaymentRequest;
import com.adyen.v6.constants.StorefrontType;
import com.adyen.v6.forms.AddressForm;


public class PlaceOrderRequest {

    private PaymentRequest paymentRequest;

    private boolean useAdyenDeliveryAddress;
    private AddressForm billingAddress;

    private StorefrontType storefrontType;
    private String storefrontVersion;
    
    // Partial payment support
    private String partialPaymentId;

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

    public StorefrontType getStorefrontType() {
        return storefrontType;
    }

    public void setStorefrontType(StorefrontType storefrontType) {
        this.storefrontType = storefrontType;
    }

    public String getStorefrontVersion() {
        return storefrontVersion;
    }

    public void setStorefrontVersion(String storefrontVersion) {
        this.storefrontVersion = storefrontVersion;
    }
    
    public String getPartialPaymentId() {
        return partialPaymentId;
    }
    
    public void setPartialPaymentId(String partialPaymentId) {
        this.partialPaymentId = partialPaymentId;
    }
}
