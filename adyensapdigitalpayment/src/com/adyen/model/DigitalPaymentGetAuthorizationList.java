package com.adyen.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DigitalPaymentGetAuthorizationList {

    @JsonProperty("Authorizations")
    private List<DigitalPaymentGetAuthorization> authorizations;
    public List<DigitalPaymentGetAuthorization> getAuthorizations() {
        return authorizations;
    }
    public void setAuthorizations(List<DigitalPaymentGetAuthorization> authorizations) {
        this.authorizations = authorizations;
    }


}
