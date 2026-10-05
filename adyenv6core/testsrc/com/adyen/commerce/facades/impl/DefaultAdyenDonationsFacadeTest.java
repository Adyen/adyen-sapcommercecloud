package com.adyen.commerce.facades.impl;

import com.adyen.commerce.data.DonationPaymentData;
import com.adyen.model.checkout.DonationCampaignsRequest;
import com.adyen.model.checkout.DonationPaymentRequest;
import com.adyen.v6.factory.AdyenPaymentServiceFactory;
import com.adyen.v6.service.AdyenDonationsService;
import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class DefaultAdyenDonationsFacadeTest {
    @Mock
    private BaseStoreService baseStoreService;
    @Mock
    private BaseStoreModel baseStore;
    @Mock
    private AdyenPaymentServiceFactory paymentServiceFactory;
    @Mock
    private AdyenDonationsService donationsService;

    @Test
    public void shouldRequestCampaignsInCurrencyOfOriginalPayment() throws Exception {
        final DefaultAdyenDonationsFacade facade = new DefaultAdyenDonationsFacade();
        facade.setBaseStoreService(baseStoreService);
        facade.setAdyenPaymentServiceFactory(paymentServiceFactory);
        when(baseStoreService.getCurrentBaseStore()).thenReturn(baseStore);
        when(baseStore.getAdyenMerchantAccount()).thenReturn("merchant");
        when(paymentServiceFactory.createAdyenDonationsService(baseStore)).thenReturn(donationsService);

        facade.getDonationCampaigns("EUR");

        final ArgumentCaptor<DonationCampaignsRequest> request = ArgumentCaptor.forClass(DonationCampaignsRequest.class);
        verify(donationsService).getDonationCampaigns(request.capture());
        assertEquals("EUR", request.getValue().getCurrency());
    }

    @Test
    public void shouldBuildAdyenDonationRequestInFacade() throws Exception {
        final DefaultAdyenDonationsFacade facade = new DefaultAdyenDonationsFacade();
        facade.setBaseStoreService(baseStoreService);
        facade.setAdyenPaymentServiceFactory(paymentServiceFactory);
        when(baseStoreService.getCurrentBaseStore()).thenReturn(baseStore);
        when(baseStore.getAdyenMerchantAccount()).thenReturn("merchant");
        when(paymentServiceFactory.createAdyenDonationsService(baseStore)).thenReturn(donationsService);

        final DonationPaymentData paymentData = new DonationPaymentData();
        paymentData.setAmountCurrency("EUR");
        paymentData.setAmountValue(500L);
        paymentData.setCampaignId("campaign");
        paymentData.setDonationToken("token");
        paymentData.setOriginalPspReference("original-psp");
        paymentData.setReference("donation-reference");
        paymentData.setReturnUrl("https://example.test/return");

        facade.makeDonationPayment(paymentData, "idempotency-key");

        final ArgumentCaptor<DonationPaymentRequest> request = ArgumentCaptor.forClass(DonationPaymentRequest.class);
        verify(donationsService).makeDonationPayment(request.capture(), org.mockito.Mockito.eq("idempotency-key"));
        assertEquals("merchant", request.getValue().getMerchantAccount());
        assertEquals("campaign", request.getValue().getDonationCampaignId());
        assertEquals(Long.valueOf(500L), request.getValue().getAmount().getValue());
    }
}
