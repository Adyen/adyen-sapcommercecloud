package com.adyen.commerce.occ.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class DonationRequest {
    @Valid @NotNull private Amount amount;
    @NotBlank private String reference;
    @Valid private PaymentMethod paymentMethod;
    @NotBlank private String donationToken;
    @NotBlank private String donationOriginalPspReference;
    @NotBlank private String donationCampaignId;
    private String returnUrl;
    @NotBlank private String merchantAccount;

    public Amount getAmount() { return amount; }
    public void setAmount(final Amount amount) { this.amount = amount; }
    public String getReference() { return reference; }
    public void setReference(final String reference) { this.reference = reference; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(final PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getDonationToken() { return donationToken; }
    public void setDonationToken(final String donationToken) { this.donationToken = donationToken; }
    public String getDonationOriginalPspReference() { return donationOriginalPspReference; }
    public void setDonationOriginalPspReference(final String value) { this.donationOriginalPspReference = value; }
    public String getDonationCampaignId() { return donationCampaignId; }
    public void setDonationCampaignId(final String donationCampaignId) { this.donationCampaignId = donationCampaignId; }
    public String getReturnUrl() { return returnUrl; }
    public void setReturnUrl(final String returnUrl) { this.returnUrl = returnUrl; }
    public String getMerchantAccount() { return merchantAccount; }
    public void setMerchantAccount(final String merchantAccount) { this.merchantAccount = merchantAccount; }

    public static class Amount {
        @NotBlank private String currency;
        @NotNull @Positive private Long value;
        public String getCurrency() { return currency; }
        public void setCurrency(final String currency) { this.currency = currency; }
        public Long getValue() { return value; }
        public void setValue(final Long value) { this.value = value; }
    }

    public static class PaymentMethod {
        private String type;
        public String getType() { return type; }
        public void setType(final String type) { this.type = type; }
    }
}
