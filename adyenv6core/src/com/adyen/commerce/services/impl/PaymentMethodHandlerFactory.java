package com.adyen.commerce.services.impl;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Factory for creating appropriate payment method handlers
 */
@Component
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
    public Optional<PaymentMethodHandler> getHandler(String paymentMethod) {
        return handlers.stream()
            .filter(handler -> handler.canHandle(paymentMethod))
            .findFirst();
    }
}