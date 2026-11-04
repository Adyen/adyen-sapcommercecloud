package com.adyen.v6.service;

import com.adyen.model.checkout.*;
import com.adyen.v6.exceptions.AdyenStoredCardsUnavailableException;
import de.hybris.platform.core.model.user.*;

import java.util.*;

public interface AdyenStoredCardsService {
	/**
	 * The customer's stored cards, or an empty list when there are none <em>or</em> when Adyen could not be
	 * asked. Fine for display; use {@link #getStoredCardsOrThrow} where the two must be told apart.
	 */
	List<StoredPaymentMethodResource> getStoredCards(CustomerModel customer);

	/**
	 * The customer's stored cards; an empty list means Adyen answered that there are none.
	 * <p>
	 * An implementation that cannot tell a failure from an empty answer keeps this default, which behaves
	 * like {@link #getStoredCards} and so never throws. An implementation that overrides this one must not
	 * call {@link #getStoredCards} from it if that one delegates here.
	 *
	 * @throws AdyenStoredCardsUnavailableException when the listing could not be read from Adyen
	 */
	default List<StoredPaymentMethodResource> getStoredCardsOrThrow(CustomerModel customer)
			throws AdyenStoredCardsUnavailableException {
		return getStoredCards(customer);
	}

	boolean disableStoredCard(CustomerModel customer, String paymentInfoId);

	String resolveCountryCode(CustomerModel customer);

	String getEnvironmentMode();

	String getCheckoutShopperHost();
}
