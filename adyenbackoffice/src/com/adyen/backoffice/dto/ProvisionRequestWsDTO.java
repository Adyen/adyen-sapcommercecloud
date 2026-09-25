package com.adyen.backoffice.dto;

import jakarta.validation.constraints.NotBlank;

/** What the wizard needs from the merchant before it can configure Adyen for a store. */
public class ProvisionRequestWsDTO {

    @NotBlank(message = "baseStore is missing")
    private String baseStore;

    @NotBlank(message = "merchantAccount is missing")
    private String merchantAccount;

    /**
     * Public origin of the storefront, for example {@code https://shop.example.com}. Registered with Adyen
     * as an allowed origin so the Drop-in may authenticate with the client key from that page.
     */
    @NotBlank(message = "storefrontOrigin is missing")
    private String storefrontOrigin;

    /**
     * Public URL Adyen will send webhooks to. Asked for rather than derived: the host a shopper reaches is
     * not something this extension can see from inside the platform.
     */
    @NotBlank(message = "notificationUrl is missing")
    private String notificationUrl;

    public String getBaseStore() { return baseStore; }

    public void setBaseStore(final String baseStore) { this.baseStore = baseStore; }

    public String getMerchantAccount() { return merchantAccount; }

    public void setMerchantAccount(final String merchantAccount) { this.merchantAccount = merchantAccount; }

    public String getStorefrontOrigin() { return storefrontOrigin; }

    public void setStorefrontOrigin(final String storefrontOrigin) { this.storefrontOrigin = storefrontOrigin; }

    public String getNotificationUrl() { return notificationUrl; }

    public void setNotificationUrl(final String notificationUrl) { this.notificationUrl = notificationUrl; }
}
