package com.adyen.v6.strategy;

import de.hybris.platform.store.BaseStoreModel;

public interface AdyenMerchantAccountStrategy {

    String getWebMerchantAccount();

    String getWebMerchantAccount(BaseStoreModel baseStore);

    String getPosMerchantAccount();

    String getPosMerchantAccount(BaseStoreModel baseStore);

}
