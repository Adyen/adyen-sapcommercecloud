package com.adyen.commerce.facades.impl;

import com.adyen.commerce.facades.AdyenOrderFacade;
import com.adyen.commerce.facades.AdyenPaymentOutcomeFacade;
import com.adyen.commerce.services.AdyenCartRestorationService;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.order.InvalidCartException;
import de.hybris.platform.order.exceptions.CalculationException;

public class DefaultAdyenPaymentOutcomeFacade
        implements AdyenPaymentOutcomeFacade {

    private AdyenOrderFacade adyenOrderFacade;
    private AdyenCartRestorationService adyenCartRestorationService;

    @Override
    public String getPaymentStatusAndRestoreCart(
            final String orderIdentifier,
            final String anonymousCheckoutGuid)
            throws CalculationException, InvalidCartException {

        final OrderModel order =
                adyenOrderFacade.getOrderModelForPaymentStatus(
                        orderIdentifier,
                        anonymousCheckoutGuid);

        if (Boolean.TRUE.equals(
                order.getAdyenCartRestorationRequired())) {

            adyenCartRestorationService
                    .restoreFailedPaymentCartIfRequired(order);
        }

        return adyenOrderFacade.getPaymentStatus(order);
    }

    public void setAdyenOrderFacade(
            final AdyenOrderFacade adyenOrderFacade) {
        this.adyenOrderFacade = adyenOrderFacade;
    }

    public void setAdyenCartRestorationService(
            final AdyenCartRestorationService service) {
        this.adyenCartRestorationService = service;
    }
}