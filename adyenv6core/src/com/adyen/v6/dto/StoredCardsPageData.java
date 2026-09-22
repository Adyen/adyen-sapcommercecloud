package com.adyen.v6.dto;

import com.adyen.model.checkout.StoredPaymentMethodResource;

import java.util.List;

public class StoredCardsPageData {

	private List<StoredPaymentMethodResource> storedCards;
	private String clientKey;
	private String countryCode;
	private String environment;
	private String checkoutShopperHost;

	public List<StoredPaymentMethodResource> getStoredCards() {
		return storedCards;
	}

	public void setStoredCards(List<StoredPaymentMethodResource> storedCards) {
		this.storedCards = storedCards;
	}

	public String getClientKey() {
		return clientKey;
	}

	public void setClientKey(String clientKey) {
		this.clientKey = clientKey;
	}

	public String getCountryCode() {
		return countryCode;
	}

	public void setCountryCode(String countryCode) {
		this.countryCode = countryCode;
	}

	public String getEnvironment() {
		return environment;
	}

	public void setEnvironment(String environment) {
		this.environment = environment;
	}

	public String getCheckoutShopperHost() {
		return checkoutShopperHost;
	}

	public void setCheckoutShopperHost(String checkoutShopperHost) {
		this.checkoutShopperHost = checkoutShopperHost;
	}
}
