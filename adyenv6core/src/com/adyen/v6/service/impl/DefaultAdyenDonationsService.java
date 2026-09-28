package com.adyen.v6.service.impl;

import com.adyen.commerce.services.AdyenRequestService;
import com.adyen.model.checkout.DonationCampaignsRequest;
import com.adyen.model.checkout.DonationCampaignsResponse;
import com.adyen.model.checkout.DonationPaymentRequest;
import com.adyen.model.checkout.DonationPaymentResponse;
import com.adyen.service.checkout.DonationsApi;
import com.adyen.service.exception.ApiException;
import com.adyen.v6.service.AbstractAdyenApiService;
import com.adyen.v6.service.AdyenDonationsService;
import de.hybris.platform.store.BaseStoreModel;
import org.springframework.retry.support.RetryTemplate;

import java.io.IOException;

public class DefaultAdyenDonationsService extends AbstractAdyenApiService implements AdyenDonationsService {

    public DefaultAdyenDonationsService(BaseStoreModel baseStore, String merchantAccount, AdyenRequestService adyenRequestService, RetryTemplate adyenCustomerInteractionRetryTemplate, RetryTemplate adyenBackgroundProcessRetryTemplate) {
        super(baseStore, merchantAccount, adyenRequestService, adyenCustomerInteractionRetryTemplate, adyenBackgroundProcessRetryTemplate);
    }

    @Override
    public DonationCampaignsResponse getDonationCampaigns(DonationCampaignsRequest request) throws IOException, ApiException {
        DonationsApi donationsApi = new DonationsApi(client);
        return donationsApi.donationCampaigns(request);
    }

    @Override
    public DonationPaymentResponse makeDonationPayment(DonationPaymentRequest request) throws IOException, ApiException {
        DonationsApi donationsApi = new DonationsApi(client);
        return donationsApi.donations(request);
    }
}
