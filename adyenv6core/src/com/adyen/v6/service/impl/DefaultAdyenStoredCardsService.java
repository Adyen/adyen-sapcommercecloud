package com.adyen.v6.service.impl;

import com.adyen.model.checkout.StoredPaymentMethodResource;
import com.adyen.service.exception.ApiException;
import com.adyen.v6.enums.AdyenRegions;
import com.adyen.v6.exceptions.AdyenStoredCardsUnavailableException;
import com.adyen.v6.factory.AdyenPaymentServiceFactory;
import com.adyen.v6.service.*;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;
import org.apache.log4j.Logger;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.adyen.v6.constants.Adyenv6coreConstants.LIVE_ENV;
import static com.adyen.v6.constants.Adyenv6coreConstants.TEST_ENV;
import static com.adyen.commerce.facades.impl.DefaultAdyenCheckoutFacade.CHECKOUT_SHOPPER_HOST_LIVE;
import static com.adyen.commerce.facades.impl.DefaultAdyenCheckoutFacade.CHECKOUT_SHOPPER_HOST_LIVE_IN;
import static com.adyen.commerce.facades.impl.DefaultAdyenCheckoutFacade.CHECKOUT_SHOPPER_HOST_TEST;

public class DefaultAdyenStoredCardsService implements AdyenStoredCardsService {

	private static final Logger LOGGER = Logger.getLogger(DefaultAdyenStoredCardsService.class);

	@Resource(name = "baseStoreService")
	private BaseStoreService baseStoreService;

	@Resource(name = "adyenPaymentServiceFactory")
	private AdyenPaymentServiceFactory adyenPaymentServiceFactory;

	/**
	 * The display read: an empty list when there are no cards or when Adyen could not be asked. It only
	 * translates the failure; to change which cards are listed, override {@link #getStoredCardsOrThrow}, which
	 * every reader in this class goes through (this one, {@code disableStoredCard} and the subscription pages).
	 */
	@Override
	public List<StoredPaymentMethodResource> getStoredCards(final CustomerModel customer) {
		try {
			return getStoredCardsOrThrow(customer);
		} catch (AdyenStoredCardsUnavailableException e) {
			LOGGER.error(e.getMessage(), e.getCause());
			return new ArrayList<>();
		}
	}

	/**
	 * Do not call {@link #getStoredCards} from an override: it delegates here, so the two would call each other.
	 */
	@Override
	public List<StoredPaymentMethodResource> getStoredCardsOrThrow(final CustomerModel customer)
			throws AdyenStoredCardsUnavailableException {
		if (customer == null) {
			LOGGER.warn("Customer is null, cannot retrieve stored cards");
			return new ArrayList<>();
		}

		try {
			return getAdyenPaymentService().getStoredCards(customer.getCustomerID());
		} catch (IOException | ApiException e) {
			throw new AdyenStoredCardsUnavailableException(
					(e instanceof ApiException ? "ApiException" : "IOException") + " while retrieving stored cards", e);
		}
	}

	@Override
	public boolean disableStoredCard(final CustomerModel customer, final String paymentInfoId) {
		if (customer == null) {
			LOGGER.warn("Customer is null, cannot disable stored card");
			return false;
		}

		if (paymentInfoId == null || paymentInfoId.isEmpty()) {
			LOGGER.warn("paymentInfoId is empty");
			return false;
		}

		final List<StoredPaymentMethodResource> storedCards;
		try {
			storedCards = getStoredCardsOrThrow(customer);
		} catch (AdyenStoredCardsUnavailableException e) {
			// Not "card not found": nothing is known about the card, so nothing is disabled.
			LOGGER.error(e.getMessage() + "; not disabling the stored card", e.getCause());
			return false;
		}
		final boolean contains = storedCards.stream()
				.anyMatch(storedCard -> paymentInfoId.equals(storedCard.getId()));

		if (!contains) {
			LOGGER.warn("Stored card with id [" + paymentInfoId + "] not found for current customer");
			return false;
		}

		try {
			getAdyenPaymentService().disableStoredCard(customer.getCustomerID(), paymentInfoId);
			return true;
		} catch (IOException e) {
			LOGGER.error("IOException while disabling stored card", e);
		} catch (ApiException e) {
			LOGGER.error("ApiException while disabling stored card", e);
		}

		return false;
	}

	@Override
	public String resolveCountryCode(final CustomerModel customer) {
		if (customer != null) {
			if (customer.getDefaultPaymentAddress() != null &&
					customer.getDefaultPaymentAddress().getCountry() != null) {
				return customer.getDefaultPaymentAddress().getCountry().getIsocode();
			}

			if (customer.getDefaultShipmentAddress() != null &&
					customer.getDefaultShipmentAddress().getCountry() != null) {
				return customer.getDefaultShipmentAddress().getCountry().getIsocode();
			}
		}

		final BaseStoreModel baseStore = baseStoreService.getCurrentBaseStore();

		if (baseStore != null &&
				baseStore.getDeliveryCountries() != null &&
				!baseStore.getDeliveryCountries().isEmpty()) {
			return baseStore.getDeliveryCountries().iterator().next().getIsocode();
		}

		return "EN";
	}

	@Override
	public String getEnvironmentMode() {
		final BaseStoreModel baseStore = baseStoreService.getCurrentBaseStore();

		if (Boolean.TRUE.equals(baseStore.getAdyenTestMode())) {
			return TEST_ENV;
		}

		if (AdyenRegions.IN.equals(baseStore.getAdyenRegion())) {
			return "live-in";
		}

		return LIVE_ENV;
	}

	@Override
	public String getCheckoutShopperHost() {
		final BaseStoreModel baseStore = baseStoreService.getCurrentBaseStore();

		if (Boolean.TRUE.equals(baseStore.getAdyenTestMode())) {
			return CHECKOUT_SHOPPER_HOST_TEST;
		}

		if (AdyenRegions.IN.equals(baseStore.getAdyenRegion())) {
			return CHECKOUT_SHOPPER_HOST_LIVE_IN;
		}

		return CHECKOUT_SHOPPER_HOST_LIVE;
	}

	protected AdyenCheckoutApiService getAdyenPaymentService() {
		final BaseStoreModel baseStore = baseStoreService.getCurrentBaseStore();
		return adyenPaymentServiceFactory.createAdyenCheckoutApiService(baseStore);
	}

	public void setBaseStoreService(final BaseStoreService baseStoreService) {
		this.baseStoreService = baseStoreService;
	}

	public void setAdyenPaymentServiceFactory(final AdyenPaymentServiceFactory adyenPaymentServiceFactory) {
		this.adyenPaymentServiceFactory = adyenPaymentServiceFactory;
	}
}