package com.adyen.v6.facades.impl;

import com.adyen.commerce.data.DataCollectionConfiguration;
import com.adyen.v6.facades.AdyenCheckoutFacade;
import com.adyen.v6.facades.AdyenDataCollectionFacade;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;
import org.springframework.util.Assert;

import java.util.Objects;

public class DefaultAdyenDataCollectionFacade implements AdyenDataCollectionFacade {

    private AdyenCheckoutFacade adyenCheckoutFacade;
    private BaseStoreService baseStoreService;

    public static String MODEL_DATA_CONFIGURATION_ENABLED = "adyenDataCollectionEnabled";


    @Override
    public DataCollectionConfiguration getDataCollectionConfiguration() {
        String checkoutShopperHost = adyenCheckoutFacade.getCheckoutShopperHost();
        BaseStoreModel currentBaseStore = baseStoreService.getCurrentBaseStore();

        Assert.notNull(currentBaseStore, "Null current BaseStore");

        DataCollectionConfiguration dataCollectionConfiguration = new DataCollectionConfiguration();
        dataCollectionConfiguration.setCheckoutShopperHost(checkoutShopperHost);
        if (Objects.nonNull(currentBaseStore.getDataCollectionEnabled())) {
            dataCollectionConfiguration.setDataCollectionEnabled(currentBaseStore.getDataCollectionEnabled());
        } else {
            dataCollectionConfiguration.setDataCollectionEnabled(false);
        }

        return dataCollectionConfiguration;
    }

    public void setAdyenCheckoutFacade(AdyenCheckoutFacade adyenCheckoutFacade) {
        this.adyenCheckoutFacade = adyenCheckoutFacade;
    }

    public void setBaseStoreService(BaseStoreService baseStoreService) {
        this.baseStoreService = baseStoreService;
    }
}
