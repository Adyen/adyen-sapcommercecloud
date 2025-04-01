package com.adyen.v6.strategy.impl;

import com.adyen.v6.strategy.AdyenMerchantAccountStrategy;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;

public class DefaultAdyenMerchantAccountStrategy implements AdyenMerchantAccountStrategy {
    private BaseStoreService baseStoreService;

    @Override
    public String getWebMerchantAccount() {
        BaseStoreModel currentBaseStore = baseStoreService.getCurrentBaseStore();
        return getWebMerchantAccount(currentBaseStore);
    }

    @Override
    public String getWebMerchantAccount(BaseStoreModel baseStore) {
        return baseStore.getAdyenMerchantAccount();
    }

    @Override
    public String getPosMerchantAccount() {
        BaseStoreModel currentBaseStore = baseStoreService.getCurrentBaseStore();
        return getPosMerchantAccount(currentBaseStore);
    }

    @Override
    public String getPosMerchantAccount(BaseStoreModel baseStore) {
        return baseStore.getAdyenPosMerchantAccount();

    }

    public void setBaseStoreService(BaseStoreService baseStoreService) {
        this.baseStoreService = baseStoreService;
    }
}
