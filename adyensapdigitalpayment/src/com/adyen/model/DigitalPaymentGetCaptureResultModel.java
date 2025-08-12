/*
 * Copyright (c) 2022 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.adyen.model;

import de.hybris.platform.cissapdigitalpayment.client.model.DigitalPaymentsTransactionModel;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DigitalPaymentGetCaptureResultModel {

    @JsonProperty("DigitalPaymentTransaction")
    private DigitalPaymentsTransactionModel digitalPaymentTransaction;
    @JsonProperty("MerchantAccount")
    private String merchantAccount;

    @JsonProperty("PaymentByDigitalPaymentService")
    private String paymentByDigitalPaymentService;

    @JsonProperty("PaymentByPaymentServicePrvdr")
    private String paymentByPaymentServicePrvdr;

    @JsonProperty("PaymentServiceProvider")
    private String paymentServiceProvider;

    public DigitalPaymentsTransactionModel getDigitalPaymentTransaction() {
        return digitalPaymentTransaction;
    }

    public void setDigitalPaymentTransaction(DigitalPaymentsTransactionModel digitalPaymentTransaction) {
        this.digitalPaymentTransaction = digitalPaymentTransaction;
    }

    public String getMerchantAccount() {
        return merchantAccount;
    }

    public void setMerchantAccount(String merchantAccount) {
        this.merchantAccount = merchantAccount;
    }


    public String getPaymentByDigitalPaymentService() {
        return paymentByDigitalPaymentService;
    }

    public void setPaymentByDigitalPaymentService(String paymentByDigitalPaymentService) {
        this.paymentByDigitalPaymentService = paymentByDigitalPaymentService;
    }

    public String getPaymentByPaymentServicePrvdr() {
        return paymentByPaymentServicePrvdr;
    }

    public void setPaymentByPaymentServicePrvdr(String paymentByPaymentServicePrvdr) {
        this.paymentByPaymentServicePrvdr = paymentByPaymentServicePrvdr;
    }

    public String getPaymentServiceProvider() {
        return paymentServiceProvider;
    }

    public void setPaymentServiceProvider(String paymentServiceProvider) {
        this.paymentServiceProvider = paymentServiceProvider;
    }
}
