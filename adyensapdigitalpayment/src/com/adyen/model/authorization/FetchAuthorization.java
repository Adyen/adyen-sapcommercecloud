package com.adyen.model.authorization;

import com.fasterxml.jackson.annotation.JsonProperty;

public class FetchAuthorization {

    @JsonProperty("AuthorizationByDigitalPaytSrvc")
    private String authorizationByDigitalPaytSrvc;

    public String getAuthorizationByDigitalPaytSrvc() {
        return authorizationByDigitalPaytSrvc;
    }

    public void setAuthorizationByDigitalPaytSrvc(String authorizationByDigitalPaytSrvc) {
        this.authorizationByDigitalPaytSrvc = authorizationByDigitalPaytSrvc;
    }
}
