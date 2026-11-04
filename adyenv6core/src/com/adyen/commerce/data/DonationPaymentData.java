package com.adyen.commerce.data;

public class DonationPaymentData {
    private Long amountValue;
    private String amountCurrency;
    private String campaignId;
    private String donationToken;
    private String originalPspReference;
    private String reference;
    private String returnUrl;

    public Long getAmountValue() { return amountValue; }
    public void setAmountValue(final Long amountValue) { this.amountValue = amountValue; }
    public String getAmountCurrency() { return amountCurrency; }
    public void setAmountCurrency(final String amountCurrency) { this.amountCurrency = amountCurrency; }
    public String getCampaignId() { return campaignId; }
    public void setCampaignId(final String campaignId) { this.campaignId = campaignId; }
    public String getDonationToken() { return donationToken; }
    public void setDonationToken(final String donationToken) { this.donationToken = donationToken; }
    public String getOriginalPspReference() { return originalPspReference; }
    public void setOriginalPspReference(final String originalPspReference) { this.originalPspReference = originalPspReference; }
    public String getReference() { return reference; }
    public void setReference(final String reference) { this.reference = reference; }
    public String getReturnUrl() { return returnUrl; }
    public void setReturnUrl(final String returnUrl) { this.returnUrl = returnUrl; }
}
