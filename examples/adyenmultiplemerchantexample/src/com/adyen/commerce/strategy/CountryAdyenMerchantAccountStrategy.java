package com.adyen.commerce.strategy;

import com.adyen.v6.enums.AdyenMerchantAccountType;
import com.adyen.v6.model.AdyenMerchantConfigModel;
import com.adyen.v6.strategy.AdyenMerchantAccountStrategy;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.user.AddressModel;
import de.hybris.platform.order.CartService;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import java.util.Optional;

public class CountryAdyenMerchantAccountStrategy implements AdyenMerchantAccountStrategy {
    private static final Logger LOG = Logger.getLogger(CountryAdyenMerchantAccountStrategy.class.getName());

    private BaseStoreService baseStoreService;
    private CartService cartService;

    @Override
    public String getWebMerchantAccount() {
        BaseStoreModel currentBaseStore = baseStoreService.getCurrentBaseStore();

        return getWebMerchantAccount(currentBaseStore);
    }

    @Override
    public String getWebMerchantAccount(BaseStoreModel baseStore) {
        CartModel sessionCart = cartService.getSessionCart();
        AddressModel deliveryAddress = sessionCart.getDeliveryAddress();

        if (deliveryAddress != null) {
            Optional<AdyenMerchantConfigModel> merchantConfigModel = baseStore.getAdyenMerchantConfig().stream().filter(amc ->
                            AdyenMerchantAccountType.WEB.equals(amc.getAdyenMerchantType()))
                    .filter(amc -> StringUtils.equalsIgnoreCase(amc.getCountry().getIsocode(), deliveryAddress.getCountry().getIsocode()))
                    .findFirst();

            if (merchantConfigModel.isPresent()) {
                return merchantConfigModel.get().getAdyenMerchantAccount();
            }
        }


        LOG.warn("No WEB merchant config, returning one from adyenMerchantAccount");
        return baseStore.getAdyenMerchantAccount();
    }

    @Override
    public String getPosMerchantAccount() {
        BaseStoreModel currentBaseStore = baseStoreService.getCurrentBaseStore();

        return getPosMerchantAccount(currentBaseStore);
    }

    @Override
    public String getPosMerchantAccount(BaseStoreModel baseStore) {
        CartModel sessionCart = cartService.getSessionCart();
        AddressModel deliveryAddress = sessionCart.getDeliveryAddress();

        if (deliveryAddress != null) {
            Optional<AdyenMerchantConfigModel> merchantConfigModel = baseStore.getAdyenMerchantConfig().stream().filter(amc ->
                            AdyenMerchantAccountType.POS.equals(amc.getAdyenMerchantType()))
                    .filter(amc -> StringUtils.equalsIgnoreCase(amc.getCountry().getIsocode(), deliveryAddress.getCountry().getIsocode()))
                    .findFirst();

            if (merchantConfigModel.isPresent()) {
                return merchantConfigModel.get().getAdyenMerchantAccount();
            }
        }

        LOG.warn("No POS merchant config, returning one from adyenMerchantAccount");
        return baseStore.getAdyenPosMerchantAccount();
    }

    public void setBaseStoreService(BaseStoreService baseStoreService) {
        this.baseStoreService = baseStoreService;
    }

    public void setCartService(CartService cartService) {
        this.cartService = cartService;
    }
}
