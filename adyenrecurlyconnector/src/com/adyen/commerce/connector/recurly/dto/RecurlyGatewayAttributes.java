package com.adyen.commerce.connector.recurly.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RecurlyGatewayAttributes
{
    @JsonProperty("account_reference")
    private String accountReference;

    public String getAccountReference() {
        return accountReference;
    }

    public void setAccountReference(String accountReference) {
        this.accountReference = accountReference;
    }
}