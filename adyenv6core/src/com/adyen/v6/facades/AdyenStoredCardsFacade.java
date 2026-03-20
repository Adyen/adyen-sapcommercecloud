package com.adyen.v6.facades;

import com.adyen.v6.dto.*;

public interface AdyenStoredCardsFacade {

	StoredCardsPageData getStoredCardsPageDataForCurrentCustomer();

	boolean removeStoredCardForCurrentCustomer(String paymentInfoId);
}
