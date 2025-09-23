package com.adyen.commerce.facades;

import com.adyen.commerce.request.GiftCardBalanceRequest;
import com.adyen.commerce.response.GiftCardBalanceResponse;

/**
 * Facade interface for Adyen gift card operations
 */
public interface AdyenGiftCardFacade {
    
    /**
     * Check gift card balance for partial payments
     * This method verifies the available balance on a gift card before processing
     * 
     * @param request The gift card balance request containing card details and amount
     * @return Response containing available balance and transaction limit
     */
    GiftCardBalanceResponse checkGiftCardBalance(GiftCardBalanceRequest request);
}