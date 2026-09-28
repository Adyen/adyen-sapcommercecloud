package com.adyen.commerce.facades;

import com.adyen.model.checkout.DonationCampaignsResponse;
import com.adyen.model.checkout.DonationPaymentRequest;
import com.adyen.model.checkout.DonationPaymentResponse;
import com.adyen.service.exception.ApiException;

import java.io.IOException;

public interface AdyenDonationsFacade {

    DonationCampaignsResponse getDonationCampaigns() throws IOException, ApiException;

    DonationPaymentResponse makeDonationPayment(DonationPaymentRequest request) throws IOException, ApiException;
}
