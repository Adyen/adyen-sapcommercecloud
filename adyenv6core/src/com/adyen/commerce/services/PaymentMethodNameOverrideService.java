package com.adyen.commerce.services;

import com.adyen.model.checkout.PaymentMethodsResponse;

public interface PaymentMethodNameOverrideService {

    PaymentMethodsResponse overridePaymentMethodNamesFromConfig(final PaymentMethodsResponse paymentMethodsResponse);
}
