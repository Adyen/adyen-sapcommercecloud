package com.adyen.commerce.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class DonationRequest {
    @Valid
    @NotNull
    private Amount amount;

    @NotBlank
    private String donationCampaignId;

    public Amount getAmount() { return amount; }
    public void setAmount(final Amount amount) { this.amount = amount; }
    public String getDonationCampaignId() { return donationCampaignId; }
    public void setDonationCampaignId(final String donationCampaignId) { this.donationCampaignId = donationCampaignId; }

    public static class Amount {
        @NotBlank
        private String currency;

        @NotNull
        @Positive
        private Long value;

        public String getCurrency() { return currency; }
        public void setCurrency(final String currency) { this.currency = currency; }
        public Long getValue() { return value; }
        public void setValue(final Long value) { this.value = value; }
    }
}
