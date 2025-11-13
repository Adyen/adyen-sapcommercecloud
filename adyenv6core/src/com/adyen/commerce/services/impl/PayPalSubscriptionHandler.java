package com.adyen.commerce.services.impl;

import com.adyen.model.checkout.CheckoutPaymentMethod;
import com.adyen.model.checkout.PayPalDetails;
import com.adyen.model.checkout.PaymentRequest;
import com.adyen.v6.enums.RecurringContractMode;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.core.model.user.CustomerModel;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Objects;

public class PayPalSubscriptionHandler implements PaymentMethodHandler {
    @Override
    public boolean canHandle(String paymentMethod) {
        return Arrays.stream(PayPalDetails.TypeEnum.values()).map(PayPalDetails.TypeEnum::toString).anyMatch(type -> type.equalsIgnoreCase(paymentMethod));
    }

    @Override
    public void updatePaymentRequest(PaymentRequest paymentRequest, CartData cartData, RecurringContractMode recurringContractMode, CustomerModel customerModel, Boolean is3DS2Allowed, Boolean guestUserTokenizationEnabled) {
        if (StringUtils.isNotEmpty(cartData.getAdyenSelectedReference())) {

            PayPalDetails payPalDetails;

            if (Objects.isNull(paymentRequest.getPaymentMethod()) || Objects.isNull(paymentRequest.getPaymentMethod().getPayPalDetails())) {
                payPalDetails = new PayPalDetails();
            } else {
                payPalDetails = paymentRequest.getPaymentMethod().getPayPalDetails();
            }

            payPalDetails.setStoredPaymentMethodId(cartData.getAdyenSelectedReference());
            CheckoutPaymentMethod checkoutPaymentMethod = new CheckoutPaymentMethod();
            checkoutPaymentMethod.setActualInstance(payPalDetails);
            paymentRequest.setPaymentMethod(checkoutPaymentMethod);

            paymentRequest.setRecurringProcessingModel(PaymentRequest.RecurringProcessingModelEnum.SUBSCRIPTION);

            paymentRequest.setShopperInteraction(PaymentRequest.ShopperInteractionEnum.CONTAUTH);
        }
    }
}
