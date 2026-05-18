package com.adyen.v6.service;

import com.adyen.model.checkout.*;
import de.hybris.platform.core.model.user.*;

import java.util.*;

public interface AdyenStoredCardsService {
	List<StoredPaymentMethodResource> getStoredCards(CustomerModel customer);

	boolean disableStoredCard(CustomerModel customer, String paymentInfoId);

	String resolveCountryCode(CustomerModel customer);

	String getEnvironmentMode();

	String getCheckoutShopperHost();
}
