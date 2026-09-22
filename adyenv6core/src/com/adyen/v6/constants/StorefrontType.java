package com.adyen.v6.constants;

public enum StorefrontType {
    ACCELERATOR("accelerator"),
    SPA("spa"),
    SPARTACUS("spartacus"),
    CUSTOM("custom"),
    EXPRESSOCC("expressocc"),
    SUBSCRIPTION("subscription");

    private final String value;

    StorefrontType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
