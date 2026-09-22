package com.adyen.commerce.handler;


import com.adyen.model.checkout.PaymentRequest;
import de.hybris.platform.core.model.order.AbstractOrderModel;

/**
 * Interface for handling different payment method specific logic
 */
public interface SubscriptionPaymentMethodHandler {

    /**
     * Checks if this handler can process the given payment method
     */
    boolean canHandle(String paymentMethod);

    /**
     * Updates the payment request with payment method specific data
     */
    void updatePaymentRequest(PaymentRequest paymentRequest, AbstractOrderModel orderModel);
}
