package com.adyen.v6.facades.impl;

import com.adyen.v6.dto.*;
import com.adyen.v6.facades.AdyenStoredCardsFacade;
import com.adyen.v6.service.AdyenStoredCardsService;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.servicelayer.user.UserService;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;
import org.apache.log4j.Logger;

import javax.annotation.Resource;

public class DefaultAdyenStoredCardsFacade implements AdyenStoredCardsFacade {

	private static final Logger LOGGER = Logger.getLogger(DefaultAdyenStoredCardsFacade.class);

	@Resource(name = "userService")
	private UserService userService;

	@Resource(name = "baseStoreService")
	private BaseStoreService baseStoreService;

	@Resource(name = "adyenStoredCardsService")
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