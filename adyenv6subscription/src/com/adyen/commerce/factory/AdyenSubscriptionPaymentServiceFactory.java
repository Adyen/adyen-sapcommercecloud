package com.adyen.commerce.factory;

import com.adyen.commerce.service.SubscriptionAdyenCheckoutApiService;
import com.adyen.commerce.service.impl.DefaultSubscriptionAdyenCheckoutApiService;
import com.adyen.commerce.services.impl.ApplicationInfoService;
import com.adyen.v6.strategy.AdyenMerchantAccountStrategy;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.core.model.user.AddressModel;
import de.hybris.platform.servicelayer.dto.converter.Converter;
import de.hybris.platform.store.BaseStoreModel;
import org.springframework.retry.support.RetryTemplate;

public class AdyenSubscriptionPaymentServiceFactory {

    protected final AdyenMerchantAccountStrategy adyenMerchantAccountStrategy;
    protected final ApplicationInfoService applicationInfoService;
    protected final Converter<AddressModel, AddressData> addressConverter;
    private final RetryTemplate adyenCustomerInteractionRetryTemplate;
    private final RetryTemplate adyenBackgroundProcessRetryTemplate;


    public AdyenSubscriptionPaymentServiceFactory(final AdyenMerchantAccountStrategy adyenMerchantAccountStrategy, final ApplicationInfoService applicationInfoService, final Converter<AddressModel, AddressData> addressConverter, final RetryTemplate adyenCustomerInteractionRetryTemplate, final RetryTemplate adyenBackgroundProcessRetryTemplate) {
        this.adyenMerchantAccountStrategy = adyenMerchantAccountStrategy;
        this.applicationInfoService = applicationInfoService;
        this.addressConverter = addressConverter;
        this.adyenCustomerInteractionRetryTemplate = adyenCustomerInteractionRetryTemplate;
        this.adyenBackgroundProcessRetryTemplate = adyenBackgroundProcessRetryTemplate;
    }

    public SubscriptionAdyenCheckoutApiService createAdyenSubscriptionCheckoutApiService(final BaseStoreModel baseStoreModel) {
        String webMerchantAccount = adyenMerchantAccountStrategy.getWebMerchantAccount(baseStoreModel);
        return new DefaultSubscriptionAdyenCheckoutApiService(baseStoreModel, webMerchantAccount, applicationInfoService, addressConverter, adyenCustomerInteractionRetryTemplate, adyenBackgroundProcessRetryTemplate);
    }
}