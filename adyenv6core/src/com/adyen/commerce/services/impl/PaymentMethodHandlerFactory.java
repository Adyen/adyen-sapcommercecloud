package com.adyen.commerce.services.impl;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Factory for creating appropriate payment method handlers
 */
public class PaymentMethodHandlerFactory {
    
    private final List<PaymentMethodHandler> handlers;

    public PaymentMethodHandlerFactory() {
        this.handlers = Arrays.asList(
            new CreditCardPaymentHandler(),
            new OneClickPaymentHandler(),
            new SchemePaymentHandler(),
            new AlternativePaymentHandler(),
            new CreditCardSubscriptionHandler(),
            new IdealSubscriptionHandler(),
            new KlarnaSubscriptionHandler(),
            new PayPalSubscriptionHandler()
        );
    }

    /**
     * Gets the appropriate handler for the given payment method
     */
    public List<PaymentMethodHandler> getHandler(String paymentMethod) {
        return handlers.stream()
            .filter(handler -> handler.canHandle(paymentMethod))
            .collect(Collectors.toUnmodifiableList());
    }
}