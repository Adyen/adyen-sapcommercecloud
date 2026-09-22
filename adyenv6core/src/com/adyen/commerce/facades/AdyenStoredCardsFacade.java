package com.adyen.commerce.facades;

import com.adyen.v6.dto.StoredCardsPageData;

/**
 * Facade responsible for managing stored payment cards for the current customer.
 */
public interface AdyenStoredCardsFacade {

    StoredCardsPageData getStoredCardsPageDataForCurrentCustomer();

    boolean removeStoredCardForCurrentCustomer(String paymentInfoId);
}
