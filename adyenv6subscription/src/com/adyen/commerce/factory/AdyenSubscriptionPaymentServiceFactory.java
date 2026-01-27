package com.adyen.commerce.factory;

import com.adyen.commerce.service.SubscriptionAdyenCheckoutApiService;
import com.adyen.commerce.service.impl.DefaultSubscriptionAdyenCheckoutApiService;
import com.adyen.commerce.services.impl.ApplicationInfoService;
import com.adyen.v6.strategy.AdyenMerchantAccountStrategy;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.core.model.user.AddressModel;
import de.hybris.platform.servicelayer.dto.converter.Converter;
import de.hybris.platform.store.BaseStoreModel;

public class AdyenSubscriptionPaymentServiceFactory {

    protected final AdyenMerchantAccountStrategy adyenMerchantAccountStrategy;
    protected final ApplicationInfoService applicationInfoService;
    protected final Converter<AddressModel, AddressData> addressConverter;


    public AdyenSubscriptionPaymentServiceFactory(final AdyenMerchantAccountStrategy adyenMerchantAccountStrategy, final ApplicationInfoService applicationInfoService, final Converter<AddressModel, AddressData> addressConverter) {
        this.adyenMerchantAccountStrategy = adyenMerchantAccountStrategy;
        this.applicationInfoService = applicationInfoService;
        this.addressConverter = addressConverter;
    }

    public SubscriptionAdyenCheckoutApiService createAdyenSubscriptionCheckoutApiService(final BaseStoreModel baseStoreModel) {
        String webMerchantAccount = adyenMerchantAccountStrategy.getWebMerchantAccount(baseStoreModel);
        return new DefaultSubscriptionAdyenCheckoutApiService(baseStoreModel, webMerchantAccount, applicationInfoService, addressConverter);
    }
}