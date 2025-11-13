package com.adyen.commerce.services.impl;

import com.adyen.model.checkout.CardDetails;
import com.adyen.model.checkout.CheckoutPaymentMethod;
import com.adyen.model.checkout.PaymentRequest;
import com.adyen.v6.enums.RecurringContractMode;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.core.model.user.CustomerModel;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Objects;

public class CreditCardSubscriptionHandler implements PaymentMethodHandler {
    @Override
    public boolean canHandle(String paymentMethod) {
        return Arrays.stream(CardDetails.TypeEnum.values()).map(CardDetails.TypeEnum::toString).anyMatch(type -> type.equalsIgnoreCase(paymentMethod));
    }

    @Override
    public void updatePaymentRequest(PaymentRequest paymentRequest, CartData cartData, RecurringContractMode recurringContractMode, CustomerModel customerModel, Boolean is3DS2Allowed, Boolean guestUserTokenizationEnabled) {
        if (StringUtils.isNotEmpty(cartData.getAdyenSelectedReference())) {

            CardDetails cardDetails;

            if (Objects.isNull(paymentRequest.getPaymentMethod()) || Objects.isNull(paymentRequest.getPaymentMethod().getCardDetails())) {
                cardDetails = new CardDetails();
            } else {
                cardDetails = paymentRequest.getPaymentMethod().getCardDetails();
            }

            cardDetails.setStoredPaymentMethodId(cartData.getAdyenSelectedReference());
            CheckoutPaymentMethod checkoutPaymentMethod = new CheckoutPaymentMethod();
            checkoutPaymentMethod.setActualInstance(cardDetails);
            paymentRequest.setPaymentMethod(checkoutPaymentMethod);

            paymentRequest.setRecurringProcessingModel(PaymentRequest.RecurringProcessingModelEnum.SUBSCRIPTION);

            paymentRequest.setShopperInteraction(PaymentRequest.ShopperInteractionEnum.CONTAUTH);
        }
    }
}
