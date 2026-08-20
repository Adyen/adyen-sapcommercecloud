package com.adyen.v6.event;

import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.servicelayer.event.events.AbstractEvent;

/**
 * Published after an asynchronous/3DS Adyen authorization has completed and its token metadata has been
 * persisted on the order.
 *
 * <p>The publisher holds it back until the transaction that wrote that metadata has committed, so a
 * listener - which runs on another thread, EventService being asynchronous by default - can read
 * storedPaymentMethodId and networkTxReference back from the database and find them there. An
 * authorization whose transaction rolls back is never announced at all.</p>
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
