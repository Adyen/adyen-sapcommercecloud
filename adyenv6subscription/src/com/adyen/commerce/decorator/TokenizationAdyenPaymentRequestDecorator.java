package com.adyen.commerce.decorator;

import com.adyen.model.checkout.PaymentRequest;
import com.adyen.v6.model.RequestInfo;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.core.model.user.CustomerModel;

import java.util.Objects;

public class TokenizationAdyenPaymentRequestDecorator implements AdyenPaymentRequestDecorator {

    @Override
    public void decoratePaymentRequest(PaymentRequest paymentRequest, CartData cartData, PaymentRequest originPaymentsRequest, RequestInfo requestInfo, CustomerModel customerModel) {
        if (tokenizeForSubscriptionProducts(cartData)) {
            paymentRequest.setStorePaymentMethod(true);
            paymentRequest.setRecurringProcessingModel(PaymentRequest.RecurringProcessingModelEnum.SUBSCRIPTION);
        }
    }

    protected boolean tokenizeForSubscriptionProducts(CartData cartData) {
        return !cartData.getSubscriptionOrder() && cartData.getEntries().stream()
                .anyMatch(entry -> Objects.nonNull(entry.getProduct().getSubscriptionTerm()));
    }
}
