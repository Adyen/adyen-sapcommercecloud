package com.adyen.v6.event;

import de.hybris.platform.core.model.order.OrderModel;

/**
 * Announces a fully cancelled order once the transaction that cancelled it has committed.
 *
 * <p>After-commit matters more here than almost anywhere else this rule is applied: a listener acts by
 * cancelling the subscription on an external billing platform, which is not something that can be rolled
 * back afterwards. Announcing before the commit would risk stopping a shopper's subscription because of an
 * order cancellation the database then threw away.</p>
 */
public class AdyenOrderCancelledEventPublisher extends AfterCommitEventPublisher {

    /**
     * @param orderModel the fully cancelled order; {@code null} publishes nothing, because a listener has
     *                   nothing it could act on without one
     */
    public void publishCancelled(final OrderModel orderModel) {
        if (orderModel == null) {
            return;
        }
        publishAfterCommit(new AdyenOrderCancelledEvent(orderModel));
    }
}
