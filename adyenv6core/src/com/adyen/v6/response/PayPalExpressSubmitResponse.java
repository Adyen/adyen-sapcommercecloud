package com.adyen.v6.response;

import com.adyen.model.checkout.PaymentResponse;

import java.io.Serializable;

public class PayPalExpressSubmitResponse implements Serializable {
    private PaymentResponse paymentResponse;
    private Serializable expressCartGuid;

    public PaymentResponse getPaymentResponse() {
        return paymentResponse;
    }

    public void setPaymentResponse(PaymentResponse paymentResponse) {
        this.paymentResponse = paymentResponse;
    }

    public Serializable getExpressCartGuid() {
        return expressCartGuid;
    }

    public void setExpressCartGuid(Serializable expressCartGuid) {
        this.expressCartGuid = expressCartGuid;
    }
}
