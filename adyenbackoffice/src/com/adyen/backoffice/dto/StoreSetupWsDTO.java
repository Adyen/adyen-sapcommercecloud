package com.adyen.backoffice.dto;

/** One store's configuration state, as the wizard lists it. Carries no credential. */
public class StoreSetupWsDTO {

    private String uid;
    private String name;
    private String merchantAccount;
    private boolean configured;

    public String getUid() { return uid; }

    public void setUid(final String uid) { this.uid = uid; }

    public String getName() { return name; }

    public void setName(final String name) { this.name = name; }

    public String getMerchantAccount() { return merchantAccount; }

    public void setMerchantAccount(final String merchantAccount) { this.merchantAccount = merchantAccount; }

    public boolean isConfigured() { return configured; }

    public void setConfigured(final boolean configured) { this.configured = configured; }
}
