package com.adyen.v6.service;

import com.adyen.model.checkout.DonationCampaignsRequest;
import com.adyen.model.checkout.DonationCampaignsResponse;
import com.adyen.model.checkout.DonationPaymentRequest;
import com.adyen.model.checkout.DonationPaymentResponse;
import com.adyen.service.exception.ApiException;

import java.io.IOException;

public interface AdyenDonationsService {

    DonationCampaignsResponse getDonationCampaigns(DonationCampaignsRequest request) throws IOException, ApiException;

    DonationPaymentResponse makeDonationPayment(DonationPaymentRequest request, String idempotencyKey) throws IOException, ApiException;
}
