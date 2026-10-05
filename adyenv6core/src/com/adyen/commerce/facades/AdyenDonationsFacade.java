package com.adyen.commerce.facades;

import com.adyen.commerce.data.DonationPaymentData;
import com.adyen.model.checkout.DonationCampaignsResponse;
import com.adyen.model.checkout.DonationPaymentResponse;
import com.adyen.service.exception.ApiException;

import java.io.IOException;

public interface AdyenDonationsFacade {

    DonationCampaignsResponse getDonationCampaigns(String currency) throws IOException, ApiException;

    DonationPaymentResponse makeDonationPayment(DonationPaymentData paymentData, String idempotencyKey) throws IOException, ApiException;
}
