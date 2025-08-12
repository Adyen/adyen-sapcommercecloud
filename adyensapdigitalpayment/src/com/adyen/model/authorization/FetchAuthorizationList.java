package com.adyen.model.authorization;

import java.util.List;

import com.adyen.model.authorization.FetchAuthorization;

import com.fasterxml.jackson.annotation.JsonProperty;

public class FetchAuthorizationList {

    @JsonProperty("Authorizations")
    private List<FetchAuthorization> fetchAuthorizationList;

    public List<FetchAuthorization> getFetchAuthorizationList() {
        return fetchAuthorizationList;
    }

    public void setFetchAuthorizationList(List<FetchAuthorization> fetchAuthorizationList) {
        this.fetchAuthorizationList = fetchAuthorizationList;
    }
}
