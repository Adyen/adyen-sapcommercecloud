package com.adyen.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MerchantAccountResultModel {

    @JsonProperty("Account")
    private String account;

    @JsonProperty("PaymentServiceProvider")
    private String paymentServiceProvider;


    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getPaymentServiceProvider() {
        return paymentServiceProvider;
    }

    public void setPaymentServiceProvider(String paymentServiceProvider) {
        this.paymentServiceProvider = paymentServiceProvider;
    }
}
