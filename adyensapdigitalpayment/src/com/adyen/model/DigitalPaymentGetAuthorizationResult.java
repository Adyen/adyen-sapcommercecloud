/*
 * Copyright (c) 2022 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.adyen.model;

import de.hybris.platform.cissapdigitalpayment.client.model.DigitalPaymentsTransactionModel;


import com.fasterxml.jackson.annotation.JsonProperty;

public class DigitalPaymentGetAuthorizationResult {

    @JsonProperty("DigitalPaymentTransaction")
    private DigitalPaymentsTransactionModel digitalPaymentTransaction;
    @JsonProperty("Authorization")
    private DigitalPaymentGetAuthorizationModel authorization;
    @JsonProperty("MerchantAccount")
    private String merchantAccount;
    @JsonProperty("PaytCardByDigitalPaymentSrvc")
    private String paytCardByDigitalPaymentSrvc;

    @JsonProperty("PaymentServiceProvider")
    private String paymentServiceProvider;

    @JsonProperty("PaytCardByPaytServiceProvider")
    private String paytCardByPaytServiceProvider;

    @JsonProperty("DgtlPaytAuthznRelationID")
    private String dgtlPaytAuthznRelationID;

    @JsonProperty("Source")
    private DigitalPaymentsSourceModel source;



    public DigitalPaymentsTransactionModel getDigitalPaymentTransaction() {
        return digitalPaymentTransaction;
    }

    public void setDigitalPaymentTransaction(DigitalPaymentsTransactionModel digitalPaymentTransaction) {
        this.digitalPaymentTransaction = digitalPaymentTransaction;
    }

    public DigitalPaymentGetAuthorizationModel getAuthorization() {
        return authorization;
    }

    public void setAuthorization(DigitalPaymentGetAuthorizationModel authorization) {
        this.authorization = authorization;
    }

    public String getMerchantAccount() {
        return merchantAccount;
    }

    public void setMerchantAccount(String merchantAccount) {
        this.merchantAccount = merchantAccount;
    }

    public String getPaytCardByDigitalPaymentSrvc() {
        return paytCardByDigitalPaymentSrvc;
    }

    public void setPaytCardByDigitalPaymentSrvc(String paytCardByDigitalPaymentSrvc) {
        this.paytCardByDigitalPaymentSrvc = paytCardByDigitalPaymentSrvc;
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

    public DigitalPaymentsSourceModel getSource() {
        return source;
    }

    public void setSource(DigitalPaymentsSourceModel source) {
        this.source = source;
    }

    public String getDgtlPaytAuthznRelationID() {
        return dgtlPaytAuthznRelationID;
    }

    public void setDgtlPaytAuthznRelationID(String dgtlPaytAuthznRelationID) {
        this.dgtlPaytAuthznRelationID = dgtlPaytAuthznRelationID;
    }
}
