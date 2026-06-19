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

    /**
     * Cancel a partial payment order on Adyen side via /orders/cancel endpoint.
     * This releases the held amount back to the gift card when the secondary payment fails
     * (e.g. 3DS challenge abandoned, credit card payment declined).
     *
     * @param pspReference The PSP reference of the partial payment order to cancel
     * @throws RuntimeException if the operation fails
     */
    void cancelPartialPaymentOrder(String pspReference);
}