package com.adyen.commerce.services.impl;

import com.adyen.model.checkout.*;
import com.adyen.v6.enums.RecurringContractMode;
import com.adyen.v6.util.AdyenUtil;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.core.model.user.CustomerModel;
import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

/**
 * Handler for one-click payments
 */
public class OneClickPaymentHandler implements PaymentMethodHandler {

    @Override
    public boolean canHandle(String paymentMethod) {
        return AdyenUtil.isOneClick(paymentMethod);
    }

    @Override
    public void updatePaymentRequest(PaymentRequest paymentRequest, CartData cartData,
                                   RecurringContractMode recurringContractMode,
                                   CustomerModel customerModel, Boolean is3DS2Allowed,
                                   Boolean guestUserTokenizationEnabled) {
        
        setOneClickPaymentMethod(paymentRequest, cartData);
        paymentRequest.setRecurringProcessingModel(PaymentRequest.RecurringProcessingModelEnum.CARDONFILE);

        if (Boolean.TRUE.equals(is3DS2Allowed)) {
            ThreeDSEnhancer.enhance3DS2(paymentRequest, cartData);
        }
    }

    private void setOneClickPaymentMethod(PaymentRequest paymentRequest, CartData cartData) {
        Optional.ofNullable(cartData.getAdyenSelectedReference())
            .filter(StringUtils::isNotEmpty)
            .map(selectedReference -> createCardDetails(cartData, selectedReference))
            .map(CheckoutPaymentMethod::new)
            .ifPresent(paymentRequest::setPaymentMethod);
    }

    private CardDetails createCardDetails(CartData cartData, String selectedReference) {
        CardDetails cardDetails = new CardDetails()
            .encryptedSecurityCode(cartData.getAdyenEncryptedSecurityCode())
            .recurringDetailReference(selectedReference);
        
        Optional.ofNullable(cartData.getAdyenCardBrand())
            .ifPresent(cardDetails::brand);
        
        return cardDetails;
    }

}