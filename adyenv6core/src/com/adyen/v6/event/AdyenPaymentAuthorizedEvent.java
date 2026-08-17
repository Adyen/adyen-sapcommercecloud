package com.adyen.v6.event;

import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.servicelayer.event.events.AbstractEvent;

/**
 * Published after an asynchronous/3DS Adyen authorization has completed and its token metadata has
 * been persisted on the order. Consumers can therefore safely read storedPaymentMethodId and NTID.
 */
public class AdyenPaymentAuthorizedEvent extends AbstractEvent
{
    private final transient OrderModel order;

    public AdyenPaymentAuthorizedEvent(final OrderModel order)
    {
        this.order = order;
    }

    public OrderModel getOrder()
    {
        return order;
    }
}
