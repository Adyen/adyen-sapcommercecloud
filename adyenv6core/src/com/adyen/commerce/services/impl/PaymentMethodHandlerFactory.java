package com.adyen.commerce.services.impl;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

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
            new AlternativePaymentHandler()
        );
    }

    /**
     * Gets the appropriate handler for the given payment method
     */
    public Optional<PaymentMethodHandler> getHandler(String paymentMethod) {
        return handlers.stream()
            .filter(handler -> handler.canHandle(paymentMethod))
            .findFirst();
    }
}