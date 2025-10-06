package com.adyen.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DigitalPaymentGetAuthorizationResultList {

    @JsonProperty("Authorizations")
    private List<DigitalPaymentGetAuthorizationResult> authorizationResults;

    public List<DigitalPaymentGetAuthorizationResult> getAuthorizationResults() {
        return authorizationResults;
    }

    public void setAuthorizationResults(final List<DigitalPaymentGetAuthorizationResult> authorizationResults) {
        this.authorizationResults = authorizationResults;
    }
}
