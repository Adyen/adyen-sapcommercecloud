package com.adyen.commerce.handler.factory;

import com.adyen.commerce.handler.*;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class SubscriptionPaymentMethodsHandlerFactory {

    private final List<SubscriptionPaymentMethodHandler> handlers;

    public SubscriptionPaymentMethodsHandlerFactory() {
        this.handlers = Arrays.asList(
                new CreditCardSubscriptionHandler(),
                new IdealSubscriptionHandler(),
                new KlarnaSubscriptionHandler(),
                new PayPalSubscriptionHandler()
        );
    }

    /**
     * Gets the appropriate handler for the given payment method
     */
    public Optional<SubscriptionPaymentMethodHandler> getHandler(String paymentMethod) {
        return handlers.stream()
                .filter(handler -> handler.canHandle(paymentMethod))
                .findFirst();
    }
}