package com.adyen.commerce.services;

import com.adyen.model.checkout.Donation;
import com.adyen.model.checkout.DonationCampaign;
import org.apache.commons.lang3.StringUtils;

public class DonationCampaignValidator {
    private static final String FIXED_AMOUNTS = "fixedAmounts";
    private static final String ROUNDUP = "roundup";

    public boolean isValid(final DonationCampaign campaign, final String currency,
                           final Long amountValue, final Long commercialTransactionValue) {
        if (campaign == null || campaign.getDonation() == null || amountValue == null || amountValue <= 0
                || StringUtils.isBlank(currency)) {
            return false;
        }

        final Donation donation = campaign.getDonation();
        if (!StringUtils.equalsIgnoreCase(currency, donation.getCurrency())) {
            return false;
        }

        if (FIXED_AMOUNTS.equals(donation.getType())) {
            return donation.getValues() != null && donation.getValues().contains(amountValue);
        }

        if (ROUNDUP.equals(donation.getType())) {
            if (commercialTransactionValue == null || commercialTransactionValue < 0
                    || donation.getMaxRoundupAmount() == null || donation.getMaxRoundupAmount() <= 0) {
                return false;
            }
            final long maxRoundupAmount = donation.getMaxRoundupAmount();
            final long expectedAmount = maxRoundupAmount - commercialTransactionValue % maxRoundupAmount;
            return amountValue == expectedAmount;
        }

        return false;
    }
}
