/*
 * Copyright (c) 2022 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.adyen.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DigitalGetPaymentCard {


    @JsonProperty("PaymentCardContext")
    private DigitalGetPaymentCardContext paymentCardContext;

    @JsonProperty("PaymentServiceProvider")
    private String paymentServiceProvider;

    @JsonProperty("PaytCardByPaytServiceProvider")
    private String paytCardByPaytServiceProvider;

    @JsonProperty("MerchantAccount")
    private String merchantAccount;

    @JsonProperty("PaytCardRegnLifeCycleType")
    private String paytCardRegnLifeCycleType;

    public String getMerchantAccount() {
        return merchantAccount;
    }

    public void setMerchantAccount(String merchantAccount) {
        this.merchantAccount = merchantAccount;
    }
    public String getPaymentServiceProvider() {
        return paymentServiceProvider;
    }

    public void setPaymentServiceProvider(String paymentServiceProvider) {
        this.paymentServiceProvider = paymentServiceProvider;
    }

    public String getPaytCardByPaytServiceProvider() {
        return paytCardByPaytServiceProvider;
    }

    public void setPaytCardByPaytServiceProvider(String paytCardByPaytServiceProvider) {
        this.paytCardByPaytServiceProvider = paytCardByPaytServiceProvider;
    }

    public String getPaytCardRegnLifeCycleType() {
        return paytCardRegnLifeCycleType;
    }

    public void setPaytCardRegnLifeCycleType(String paytCardRegnLifeCycleType) {
        this.paytCardRegnLifeCycleType = paytCardRegnLifeCycleType;
    }

    public DigitalGetPaymentCardContext getPaymentCardContext() {
        return paymentCardContext;
    }

    public void setPaymentCardContext(DigitalGetPaymentCardContext paymentCardContext) {
        this.paymentCardContext = paymentCardContext;
    }


}
