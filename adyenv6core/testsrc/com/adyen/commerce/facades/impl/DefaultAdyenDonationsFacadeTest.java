package com.adyen.commerce.facades.impl;

import com.adyen.model.checkout.DonationCampaignsRequest;
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
}
