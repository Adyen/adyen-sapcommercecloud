package com.adyen.v6.actions.order;

import de.hybris.platform.core.enums.OrderStatus;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.orderprocessing.model.OrderProcessModel;
import de.hybris.platform.processengine.action.AbstractAction;

import java.util.Set;

public class AdyenClassifyPaymentFailureAction
        extends AbstractAction<OrderProcessModel> {

    public static final String PAYMENT_FAILED = "PAYMENT_FAILED";
    public static final String TECHNICAL_ERROR = "TECHNICAL_ERROR";

    @Override
    public String execute(final OrderProcessModel process) {
        final OrderModel order = process.getOrder();

        if (order == null) {
            return TECHNICAL_ERROR;
        }

        if (OrderStatus.CANCELLED.equals(order.getStatus())
                || OrderStatus.PAYMENT_NOT_AUTHORIZED.equals(
                order.getStatus())) {

            return PAYMENT_FAILED;
        }

        return TECHNICAL_ERROR;
    }

    @Override
    public Set<String> getTransitions() {
        return Set.of(PAYMENT_FAILED, TECHNICAL_ERROR);
    }
}