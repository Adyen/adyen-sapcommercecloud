package com.adyen.v6.service;

import com.adyen.model.checkout.PaymentResponseAction;
import de.hybris.platform.core.model.order.OrderModel;

/**
 * Handles data specific to voucher payment methods (Konbini, Boleto, Multibanco...),
 * which return the PresentToShopper result code and are paid by the shopper later.
 */
public interface AdyenVoucherService {

    /**
     * Stores the voucher expiry date from the response action on the order.
     * Does nothing if the action is not a voucher action or the expiry is missing/unparseable.
     *
     * @param order  placed order
     * @param action action from the Adyen payment response (may be null)
     */
    void storeVoucherExpiry(OrderModel order, PaymentResponseAction action);
}
