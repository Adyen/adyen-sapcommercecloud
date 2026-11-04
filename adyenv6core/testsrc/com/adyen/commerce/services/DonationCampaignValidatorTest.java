package com.adyen.commerce.services;

import com.adyen.model.checkout.Donation;
import com.adyen.model.checkout.DonationCampaign;
import de.hybris.bootstrap.annotations.UnitTest;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@UnitTest
public class DonationCampaignValidatorTest {
    private final DonationCampaignValidator validator = new DonationCampaignValidator();

    @Test
    public void shouldAcceptOnlyConfiguredFixedAmountInCampaignCurrency() {
        final DonationCampaign campaign = campaign(new Donation()
                .type("fixedAmounts")
                .currency("EUR")
                .values(Arrays.asList(100L, 500L, 1000L)));

        assertTrue(validator.isValid(campaign, "EUR", 500L, 1999L));
        assertFalse(validator.isValid(campaign, "EUR", 501L, 1999L));
        assertFalse(validator.isValid(campaign, "USD", 500L, 1999L));
    }

    @Test
    public void shouldAcceptOnlyCalculatedRoundupAmount() {
        final DonationCampaign campaign = campaign(new Donation()
                .type("roundup")
                .currency("EUR")
                .maxRoundupAmount(100L));

        assertTrue(validator.isValid(campaign, "EUR", 1L, 1999L));
        assertFalse(validator.isValid(campaign, "EUR", 100L, 1999L));
        assertFalse(validator.isValid(campaign, "EUR", 1L, null));
    }

    private DonationCampaign campaign(final Donation donation) {
        return new DonationCampaign().id("campaign-id").donation(donation);
    }
}
