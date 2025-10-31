package com.adyen.commerce.facades;

import com.adyen.commerce.request.PartialPaymentOrderRequest;
import com.adyen.commerce.response.PartialPaymentOrderResponse;

/**
 * Facade interface for Adyen order operations
 */
public interface AdyenPartialPaymentOrderFacade {
    
    /**
     * Create a partial payment order for gift cards
     * This method is called when a gift card needs to be processed as part of a partial payment flow
     *
     * @param request The partial payment order request containing amount and payment method details
     * @return Response containing order data and PSP reference
     * @throws RuntimeException if the operation fails
     */
    PartialPaymentOrderResponse createPartialPaymentOrder(PartialPaymentOrderRequest request);
}