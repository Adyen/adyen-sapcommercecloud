package com.adyen.v6.event;

import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.servicelayer.event.events.AbstractEvent;

/**
 * Published when an order has been cancelled in full.
 *
 * <p>It exists because cancelling an order has consequences beyond the payment, and the code that knows
 * about those consequences cannot be reached from here. A subscription activated from this order goes on
 * billing the shopper every period until something stops it, and the extension that could stop it depends
 * on this one rather than the other way round. So the fact is announced and whoever cares subscribes.</p>
 *
 * <p>Only complete cancellations are announced. A partial cancellation leaves the order — and anything
 * sold on it — standing, and this integration refuses partial cancellation anyway
 * ({@code AdyenPartialOrderCancelDenialStrategy}).</p>
 */
public class AdyenOrderCancelledEvent extends AbstractEvent
{
    private final transient OrderModel order;

    public AdyenOrderCancelledEvent(final OrderModel order)
    {
        this.order = order;
    }

    public OrderModel getOrder()
    {
        return order;
    }
}
