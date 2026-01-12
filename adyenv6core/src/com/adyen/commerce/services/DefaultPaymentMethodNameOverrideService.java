package com.adyen.commerce.services;

import com.adyen.model.checkout.PaymentMethodsResponse;

public interface DefaultPaymentMethodNameOverrideService {

    PaymentMethodsResponse overridePaymentMethodNamesFromConfig(final PaymentMethodsResponse paymentMethodsResponse);
}
