package com.adyen.commerce.facades.impl;

import com.adyen.commerce.facades.AdyenStoredCardsFacade;
import com.adyen.v6.dto.StoredCardsPageData;
import com.adyen.v6.service.AdyenStoredCardsService;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.servicelayer.user.UserService;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of {@link AdyenStoredCardsFacade}.
 */
public class DefaultAdyenStoredCardsFacade implements AdyenStoredCardsFacade {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAdyenStoredCardsFacade.class);

    private UserService userService;
    private BaseStoreService baseStoreService;
    private AdyenStoredCardsService adyenStoredCardsService;

    @Override
    public StoredCardsPageData getStoredCardsPageDataForCurrentCustomer() {
        final CustomerModel customer = getCurrentCustomer();
        final BaseStoreModel baseStore = baseStoreService.getCurrentBaseStore();

        final StoredCardsPageData pageData = new StoredCardsPageData();
        pageData.setStoredCards(adyenStoredCardsService.getStoredCards(customer));
        pageData.setClientKey(baseStore.getAdyenClientKey());
        pageData.setCountryCode(adyenStoredCardsService.resolveCountryCode(customer));
        pageData.setEnvironment(adyenStoredCardsService.getEnvironmentMode());
        pageData.setCheckoutShopperHost(adyenStoredCardsService.getCheckoutShopperHost());

        return pageData;
    }

    @Override
    public boolean removeStoredCardForCurrentCustomer(final String paymentInfoId) {
        final CustomerModel customer = getCurrentCustomer();

        if (customer == null) {
            LOGGER.warn("Customer not found");
            return false;
        }

        return adyenStoredCardsService.disableStoredCard(customer, paymentInfoId);
    }

    protected CustomerModel getCurrentCustomer() {
        if (userService.isAnonymousUser(userService.getCurrentUser())) {
            LOGGER.warn("Anonymous user cannot manage stored cards");
            return null;
        }

        if (!(userService.getCurrentUser() instanceof CustomerModel)) {
            LOGGER.warn("Current user is not a CustomerModel");
            return null;
        }

        return (CustomerModel) userService.getCurrentUser();
    }

    public void setUserService(final UserService userService) {
        this.userService = userService;
    }

    public void setBaseStoreService(final BaseStoreService baseStoreService) {
        this.baseStoreService = baseStoreService;
    }

    public void setAdyenStoredCardsService(final AdyenStoredCardsService adyenStoredCardsService) {
        this.adyenStoredCardsService = adyenStoredCardsService;
    }
}
