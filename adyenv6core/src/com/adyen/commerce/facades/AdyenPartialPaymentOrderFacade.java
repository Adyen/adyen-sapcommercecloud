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

    /**
     * Cancel the Adyen order identified by an explicit order pair via /orders/cancel.
     * The record's own pspReference is the balance-check one, which Adyen does not know as an order; the order
     * PSP reference and its latest orderData come from the payment request/response.
     *
     * @param pspReference      key of the stored partial payment, looked up and marked CANCELLED on success
     * @param orderPspReference PSP reference of the Adyen order to cancel
     * @param orderData         latest orderData of that order
     * @throws RuntimeException if the operation fails
     */
    void cancelPartialPaymentOrder(String pspReference, String orderPspReference, String orderData);
}
