/*
 * Copyright (c) 2022 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.adyen.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DigitalPaymentGetCardWithAuthorizationResultModelList {

    @JsonProperty("PaymentCardsWithAuthorization")
    private List<DigitalPaymentGetAuthorizationResult> paymentCardsWithAuthorization;

    public List<DigitalPaymentGetAuthorizationResult> getPaymentCardsWithAuthorization() {
        return paymentCardsWithAuthorization;
    }

    public void setPaymentCardsWithAuthorization(List<DigitalPaymentGetAuthorizationResult> paymentCardsWithAuthorization) {
        this.paymentCardsWithAuthorization = paymentCardsWithAuthorization;
    }
}
