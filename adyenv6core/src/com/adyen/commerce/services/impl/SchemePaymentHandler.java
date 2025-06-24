package com.adyen.commerce.services.impl;

import com.adyen.model.checkout.PaymentRequest;
import com.adyen.v6.enums.RecurringContractMode;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commerceservices.enums.CustomerType;
import de.hybris.platform.core.model.user.CustomerModel;

import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_SCHEME;

/**
 * Handler for scheme payments (dual branded cards)
 */
public class SchemePaymentHandler implements PaymentMethodHandler {

    @Override
    public boolean canHandle(String paymentMethod) {
        return PAYMENT_METHOD_SCHEME.equals(paymentMethod);
    }

    @Override
    public void updatePaymentRequest(PaymentRequest paymentRequest, CartData cartData,
                                   RecurringContractMode recurringContractMode,
                                   CustomerModel customerModel, Boolean is3DS2Allowed,
                                   Boolean guestUserTokenizationEnabled) {
        
        // For scheme payments, we need the original payment request to copy settings
        // This handler assumes the original payment request data is already set
        paymentRequest.setRecurringProcessingModel(PaymentRequest.RecurringProcessingModelEnum.CARDONFILE);

        if (Boolean.TRUE.equals(is3DS2Allowed)) {
            ThreeDSEnhancer.enhance3DS2(paymentRequest, cartData);
        }

        if (customerModel.getType() == CustomerType.GUEST && Boolean.TRUE.equals(guestUserTokenizationEnabled)) {
            paymentRequest.setEnableOneClick(false);
        }
    }

}