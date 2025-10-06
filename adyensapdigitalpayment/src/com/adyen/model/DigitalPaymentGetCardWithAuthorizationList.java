package com.adyen.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DigitalPaymentGetCardWithAuthorizationList {

    @JsonProperty("PaymentCardsWithAuthorization")
    private List<DigitalPaymentGetAuthorization> paymentCardsWithAuthorization;

    public List<DigitalPaymentGetAuthorization> getPaymentCardsWithAuthorization() {
        return paymentCardsWithAuthorization;
    }

    public void setPaymentCardsWithAuthorization(List<DigitalPaymentGetAuthorization> paymentCardsWithAuthorization) {
        this.paymentCardsWithAuthorization = paymentCardsWithAuthorization;
    }
}
