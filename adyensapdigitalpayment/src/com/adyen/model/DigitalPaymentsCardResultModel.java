/*
 * Copyright (c) 2022 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.adyen.model;

import de.hybris.platform.cissapdigitalpayment.client.model.DigitalPaymentsTransactionModel;

import com.adyen.model.DigitalPaymentsSourceModel;

import com.fasterxml.jackson.annotation.JsonProperty;


public class DigitalPaymentsCardResultModel {

    @JsonProperty("DigitalPaymentTransaction")
    private DigitalPaymentsTransactionModel digitalPaymentTransaction;

    @JsonProperty("PaymentServiceProvider")
    private String paymentServiceProvider;

    @JsonProperty("PaytCardByPaytServiceProvider")
    private String paytCardByPaytServiceProvider;

    @JsonProperty("Source")
    private DigitalPaymentsSourceModel source;

    public void setDigitalPaymentTransaction(DigitalPaymentsTransactionModel digitalPaymentTransaction) {
        this.digitalPaymentTransaction = digitalPaymentTransaction;
    }

    public void setPaymentServiceProvider(String paymentServiceProvider) {
        this.paymentServiceProvider = paymentServiceProvider;
    }

    public void setPaytCardByPaytServiceProvider(String paytCardByPaytServiceProvider) {
        this.paytCardByPaytServiceProvider = paytCardByPaytServiceProvider;
    }

    public void setSource(DigitalPaymentsSourceModel source) {
        this.source = source;
    }

    public DigitalPaymentsTransactionModel getDigitalPaymentTransaction() {
        return digitalPaymentTransaction;
    }

    public DigitalPaymentsSourceModel getSource() {
        return source;
    }

    public String getPaymentServiceProvider() {
        return paymentServiceProvider;
    }

    public String getPaytCardByPaytServiceProvider() {
        return paytCardByPaytServiceProvider;
    }



}
