/*
 * Copyright (c) 2022 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.adyen.model;

import com.adyen.model.PaymentCardResult;

import com.fasterxml.jackson.annotation.JsonProperty;


public class DigitalPaymentsSourceModel {

    @JsonProperty("Card")
    private PaymentCardResult card;

    @JsonProperty("Merchant")
    private MerchantAccountResultModel merchant;


    public void setCard(PaymentCardResult card) {
        this.card = card;
    }

    public PaymentCardResult getCard() {
        return card;
    }

    public MerchantAccountResultModel getMerchant() {
        return merchant;
    }

    public void setMerchant(MerchantAccountResultModel merchant) {
        this.merchant = merchant;
    }
}
