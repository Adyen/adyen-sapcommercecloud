package com.adyen.commerce.api.response;

import com.adyen.model.checkout.DonationCampaign;

import java.util.List;

public class DonationContextResponse {
    private final String clientKey;
    private final String environment;
    private final String locale;
    private final Long commercialTxAmount;
    private final String currency;
    private final String countryCode;
    private final String checkoutShopperHost;
    private final List<DonationCampaign> campaigns;

    public DonationContextResponse(final String clientKey, final String environment, final String locale,
                                   final Long commercialTxAmount, final String currency, final String countryCode,
                                   final String checkoutShopperHost, final List<DonationCampaign> campaigns) {
        this.clientKey = clientKey;
        this.environment = environment;
        this.locale = locale;
        this.commercialTxAmount = commercialTxAmount;
        this.currency = currency;
        this.countryCode = countryCode;
        this.checkoutShopperHost = checkoutShopperHost;
        this.campaigns = campaigns;
    }

    public String getClientKey() { return clientKey; }
    public String getEnvironment() { return environment; }
    public String getLocale() { return locale; }
    public Long getCommercialTxAmount() { return commercialTxAmount; }
    public String getCurrency() { return currency; }
    public String getCountryCode() { return countryCode; }
    public String getCheckoutShopperHost() { return checkoutShopperHost; }
    public List<DonationCampaign> getCampaigns() { return campaigns; }
}
