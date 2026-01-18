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

import com.adyen.commerce.services.impl.DefaultAdyenRequestService;
import com.adyen.v6.service.*;
import com.adyen.v6.strategy.AdyenMerchantAccountStrategy;
import de.hybris.platform.store.BaseStoreModel;
import org.springframework.context.annotation.Lazy;


public class AdyenPaymentServiceFactory {

    protected final AdyenMerchantAccountStrategy adyenMerchantAccountStrategy;
    private final DefaultAdyenRequestService defaultAdyenRequestService;


    public AdyenPaymentServiceFactory(final AdyenMerchantAccountStrategy adyenMerchantAccountStrategy, @Lazy DefaultAdyenRequestService defaultAdyenRequestService) {
        this.adyenMerchantAccountStrategy = adyenMerchantAccountStrategy;
        this.defaultAdyenRequestService = defaultAdyenRequestService;
    }
    
    public AdyenCheckoutApiService createAdyenCheckoutApiService(final BaseStoreModel baseStoreModel) {
        String webMerchantAccount = adyenMerchantAccountStrategy.getWebMerchantAccount(baseStoreModel);
        DefaultAdyenCheckoutApiService defaultAdyenCheckoutApiService = new DefaultAdyenCheckoutApiService(baseStoreModel, webMerchantAccount, defaultAdyenRequestService);
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
