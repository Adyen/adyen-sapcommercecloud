/*
 *                        ######
 *                        ######
 *  ############    ####( ######  #####. ######  ############   ############
 *  #############  #####( ######  #####. ######  #############  #############
 *         ######  #####( ######  #####. ######  #####  ######  #####  ######
 *  ###### ######  #####( ######  #####. ######  #####  #####   #####  ######
 *  ###### ######  #####( ######  #####. ######  #####          #####  ######
 *  #############  #############  #############  #############  #####  ######
 *   ############   ############  #############   ############  #####  ######
 *                                       ######
 *                                #############
 *                                ############
 *
 *  Adyen Hybris Extension
 *
 *  Copyright (c) 2017 Adyen B.V.
 *  This file is open source and available under the MIT license.
 *  See the LICENSE file for more info.
 */
package com.adyen.v6.factory;

import com.adyen.commerce.services.DefaultPaymentMethodNameOverrideService;
import com.adyen.commerce.services.impl.DefaultAdyenRequestService;
import com.adyen.v6.service.*;
import com.adyen.v6.strategy.AdyenMerchantAccountStrategy;
import de.hybris.platform.store.BaseStoreModel;


public class AdyenPaymentServiceFactory {

    protected final AdyenMerchantAccountStrategy adyenMerchantAccountStrategy;
    private final DefaultAdyenRequestService defaultAdyenRequestService;
    private final DefaultPaymentMethodNameOverrideService paymentMethodNameOverrideService;


    public AdyenPaymentServiceFactory(final AdyenMerchantAccountStrategy adyenMerchantAccountStrategy, DefaultAdyenRequestService defaultAdyenRequestService, DefaultPaymentMethodNameOverrideService paymentMethodNameOverrideService) {
        this.adyenMerchantAccountStrategy = adyenMerchantAccountStrategy;
        this.defaultAdyenRequestService = defaultAdyenRequestService;
        this.paymentMethodNameOverrideService = paymentMethodNameOverrideService;
    }
    
    public AdyenCheckoutApiService createAdyenCheckoutApiService(final BaseStoreModel baseStoreModel) {
        String webMerchantAccount = adyenMerchantAccountStrategy.getWebMerchantAccount(baseStoreModel);
        DefaultAdyenCheckoutApiService defaultAdyenCheckoutApiService = new DefaultAdyenCheckoutApiService(baseStoreModel, webMerchantAccount, defaultAdyenRequestService, paymentMethodNameOverrideService);
        return defaultAdyenCheckoutApiService;
    }

    public AdyenModificationsApiService createAdyenModificationsApiService(final BaseStoreModel baseStoreModel) {
        String webMerchantAccount = adyenMerchantAccountStrategy.getWebMerchantAccount(baseStoreModel);
        DefaultAdyenModificationsApiService adyenModificationsApiService = new DefaultAdyenModificationsApiService(baseStoreModel, webMerchantAccount, defaultAdyenRequestService);
        return adyenModificationsApiService;
    }

    public AdyenUtilityApiService createAdyenUtilityApiService(final BaseStoreModel baseStoreModel) {
        String webMerchantAccount = adyenMerchantAccountStrategy.getWebMerchantAccount(baseStoreModel);
        DefaultAdyenUtilityApiService adyenUtilityApiService = new DefaultAdyenUtilityApiService(baseStoreModel, webMerchantAccount, defaultAdyenRequestService);
        return adyenUtilityApiService;
    }
}
