package com.adyen.commerce.response;

import com.adyen.model.checkout.PaymentResponse;

public class PayPalIntermediateResponse {
    private PaymentResponse paymentResponse;
    private String expressCartGuid;

    public PaymentResponse getPaymentResponse() {
        return paymentResponse;
    }

    public void setPaymentResponse(PaymentResponse paymentResponse) {
        this.paymentResponse = paymentResponse;
    }

    public String getExpressCartGuid() {
        return expressCartGuid;
    }

    public void setExpressCartGuid(String expressCartGuid) {
        this.expressCartGuid = expressCartGuid;
    }
}
