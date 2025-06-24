package com.adyen.commerce.services.impl;

import com.adyen.model.checkout.PaymentRequest;
import com.adyen.v6.enums.RecurringContractMode;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.core.model.user.CustomerModel;

/**
 * Interface for handling different payment method specific logic
 */
public interface PaymentMethodHandler {
    
    /**
     * Checks if this handler can process the given payment method
     */
    boolean canHandle(String paymentMethod);
    
    /**
     * Updates the payment request with payment method specific data
     */
    void updatePaymentRequest(PaymentRequest paymentRequest, CartData cartData, 
                            RecurringContractMode recurringContractMode, 
                            CustomerModel customerModel, Boolean is3DS2Allowed,
                            Boolean guestUserTokenizationEnabled);
}