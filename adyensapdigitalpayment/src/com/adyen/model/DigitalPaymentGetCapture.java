package com.adyen.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DigitalPaymentGetCapture {

    @JsonProperty("AmountInPaymentCurrency")
    private String amountInPaymentCurrency;
    @JsonProperty("DigitalPaymentDirectCaptureType")
    private String digitalPaymentDirectCaptureType;
    @JsonProperty("MerchantAccount")
    private String merchantAccount;
    @JsonProperty("PaymentByPaymentServicePrvdr")
    private String paymentByPaymentServicePrvdr;
    @JsonProperty("PaymentCurrency")
    private String paymentCurrency;
    @JsonProperty("PaymentServiceProvider")
    private String paymentServiceProvider;


    public String getAmountInPaymentCurrency() {
        return amountInPaymentCurrency;
    }

    public void setAmountInPaymentCurrency(String amountInPaymentCurrency) {
        this.amountInPaymentCurrency = amountInPaymentCurrency;
    }

    public String getDigitalPaymentDirectCaptureType() {
        return digitalPaymentDirectCaptureType;
    }

    public void setDigitalPaymentDirectCaptureType(String digitalPaymentDirectCaptureType) {
        this.digitalPaymentDirectCaptureType = digitalPaymentDirectCaptureType;
    }

    public String getMerchantAccount() {
        return merchantAccount;
    }

    public void setMerchantAccount(String merchantAccount) {
        this.merchantAccount = merchantAccount;
    }

    public String getPaymentByPaymentServicePrvdr() {
        return paymentByPaymentServicePrvdr;
    }

    public void setPaymentByPaymentServicePrvdr(String paymentByPaymentServicePrvdr) {
        this.paymentByPaymentServicePrvdr = paymentByPaymentServicePrvdr;
    }

    public String getPaymentCurrency() {
        return paymentCurrency;
    }

    public void setPaymentCurrency(String paymentCurrency) {
        this.paymentCurrency = paymentCurrency;
    }

    public String getPaymentServiceProvider() {
        return paymentServiceProvider;
    }

    public void setPaymentServiceProvider(String paymentServiceProvider) {
        this.paymentServiceProvider = paymentServiceProvider;
    }
}
