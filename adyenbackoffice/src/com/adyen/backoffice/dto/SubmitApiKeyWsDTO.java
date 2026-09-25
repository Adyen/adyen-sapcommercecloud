package com.adyen.backoffice.dto;

import jakarta.validation.constraints.NotBlank;

/** What the wizard posts: which store is being configured, and the key to configure it with. */
public class SubmitApiKeyWsDTO {

    @NotBlank(message = "baseStore is missing")
    private String baseStore;

    @NotBlank(message = "apiKey is missing")
    private String apiKey;

    public String getBaseStore() {
        return baseStore;
    }

    public void setBaseStore(final String baseStore) {
        this.baseStore = baseStore;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(final String apiKey) {
        this.apiKey = apiKey;
    }
}
