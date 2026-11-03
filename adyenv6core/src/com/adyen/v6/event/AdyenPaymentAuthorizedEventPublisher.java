package com.adyen.v6.event;

import de.hybris.platform.core.model.order.OrderModel;

/**
 * Announces an Adyen authorization to whoever is listening, but only once the transaction that persisted
 * it has committed.
 *
 * <p>Shared rather than inlined at each call site because there is more than one moment at which an order
 * becomes authorized-and-tokenized: an ordinary payment reaches it inside the place-order call, a 3DS or
 * redirect payment only when the shopper returns. Both have to announce the same thing under the same
 * durability rule, and a second hand-written copy of that rule is how the two would quietly stop agreeing.</p>
 *
 * <p>The durability rule itself lives in {@link AfterCommitEventPublisher}. Here it matters particularly:
 * the listeners read the order's PaymentInfo back on another thread, and before the commit that field does
 * not yet carry the token or the networkTxReference they need.</p>
 */
public class AdyenPaymentAuthorizedEventPublisher extends AfterCommitEventPublisher {

    /**
     * @param orderModel the authorized order; {@code null} is tolerated and publishes nothing, because an
     *                   activation without an order could not be recorded or retried anyway
     */
    public void publishAuthorized(final OrderModel orderModel) {
        if (orderModel == null) {
            return;
        }
        publishAfterCommit(new AdyenPaymentAuthorizedEvent(orderModel));
    }
}
