package com.adyen.commerce.facades.impl;

import com.adyen.commerce.data.DonationPaymentData;
import com.adyen.commerce.facades.AdyenDonationsFacade;
import com.adyen.model.checkout.*;
import com.adyen.service.exception.ApiException;
import com.adyen.v6.factory.AdyenPaymentServiceFactory;
import com.adyen.v6.service.AdyenDonationsService;
import de.hybris.platform.servicelayer.i18n.CommonI18NService;
import de.hybris.platform.servicelayer.i18n.I18NService;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Locale;

public class DefaultAdyenDonationsFacade implements AdyenDonationsFacade {

    private BaseStoreService baseStoreService;
    private I18NService i18NService;
    private CommonI18NService commonI18NService;
    private AdyenPaymentServiceFactory adyenPaymentServiceFactory;

    @Override
    public DonationCampaignsResponse getDonationCampaigns(final String currency) throws IOException, ApiException {
        final BaseStoreModel baseStoreModel = baseStoreService.getCurrentBaseStore();
        if (baseStoreModel == null) {
            throw new IllegalStateException("No current base store available for loading Adyen donation campaigns");
        }

        final AdyenDonationsService adyenDonationsService = adyenPaymentServiceFactory.createAdyenDonationsService(baseStoreModel);

        final DonationCampaignsRequest request = new DonationCampaignsRequest();

        request.setCurrency(StringUtils.isNotBlank(currency) ? currency : resolveCurrencyIsoCode(baseStoreModel));
        request.setLocale(resolveLocale());
        request.setMerchantAccount(resolveMerchantAccount(baseStoreModel));

        return adyenDonationsService.getDonationCampaigns(request);
    }

    @Override
    public DonationPaymentResponse makeDonationPayment(final DonationPaymentData paymentData,
                                                       final String idempotencyKey) throws IOException, ApiException {
        final BaseStoreModel baseStoreModel = baseStoreService.getCurrentBaseStore();
        if (baseStoreModel == null) {
            throw new IllegalStateException("No current base store available for Adyen donation payment");
        }

        final AdyenDonationsService adyenDonationsService = adyenPaymentServiceFactory.createAdyenDonationsService(baseStoreModel);

        return adyenDonationsService.makeDonationPayment(createDonationPaymentRequest(paymentData, baseStoreModel),
                idempotencyKey);
    }

    private String resolveCurrencyIsoCode(final BaseStoreModel baseStoreModel) {
        if (commonI18NService != null && commonI18NService.getCurrentCurrency() != null && commonI18NService.getCurrentCurrency().getIsocode() != null) {
            return commonI18NService.getCurrentCurrency().getIsocode();
        }
        if (baseStoreModel.getDefaultCurrency() != null && baseStoreModel.getDefaultCurrency().getIsocode() != null) {
            return baseStoreModel.getDefaultCurrency().getIsocode();
        }
        throw new IllegalStateException("No currency available for loading Adyen donation campaigns");
    }

    private String resolveLocale() {
        if (i18NService != null && i18NService.getCurrentLocale() != null) {
            return i18NService.getCurrentLocale().toString();
        }
        Locale systemLocale = Locale.getDefault();
        return systemLocale != null ? systemLocale.toString() : Locale.ENGLISH.toString();
    }

    private String resolveMerchantAccount(final BaseStoreModel baseStoreModel) {
        if (baseStoreModel.getAdyenMerchantAccount() != null && !baseStoreModel.getAdyenMerchantAccount().trim().isEmpty()) {
            return baseStoreModel.getAdyenMerchantAccount();
        }
        throw new IllegalStateException("No Adyen merchant account configured for loading donation campaigns");
    }

    protected DonationPaymentRequest createDonationPaymentRequest(final DonationPaymentData paymentData,
                                                                   final BaseStoreModel baseStoreModel) {
        final DonationPaymentRequest request = new DonationPaymentRequest();
        request.setAmount(new Amount().currency(paymentData.getAmountCurrency()).value(paymentData.getAmountValue()));
        request.setDonationCampaignId(paymentData.getCampaignId());
        request.setDonationToken(paymentData.getDonationToken());
        request.setDonationOriginalPspReference(paymentData.getOriginalPspReference());
        request.setMerchantAccount(resolveMerchantAccount(baseStoreModel));
        request.setReference(paymentData.getReference());
        request.setPaymentMethod(new DonationPaymentMethod(new CardDonations().type(CardDonations.TypeEnum.SCHEME)));
        request.setReturnUrl(paymentData.getReturnUrl());
        return request;
    }


    public void setBaseStoreService(final BaseStoreService baseStoreService) {
        this.baseStoreService = baseStoreService;
    }

    public void setAdyenPaymentServiceFactory(final AdyenPaymentServiceFactory adyenPaymentServiceFactory) {
        this.adyenPaymentServiceFactory = adyenPaymentServiceFactory;
    }

    public void setI18NService(final I18NService i18NService) {
        this.i18NService = i18NService;
    }

    public void setCommonI18NService(final CommonI18NService commonI18NService) {
        this.commonI18NService = commonI18NService;
    }
}
