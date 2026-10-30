package com.adyen.commerce.facades;

import de.hybris.platform.order.InvalidCartException;
import de.hybris.platform.order.exceptions.CalculationException;

public interface AdyenPaymentOutcomeFacade {

    String getPaymentStatusAndRestoreCart(
            String orderIdentifier,
            String anonymousCheckoutGuid)
            throws CalculationException, InvalidCartException;
}