package com.adyen.v6.backoffice.widgets.actions.cancel;

import com.hybris.cockpitng.actions.ActionContext;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.omsbackoffice.actions.order.cancel.CancelOrderAction;
import org.apache.commons.collections4.CollectionUtils;


/**
 * Adyen extension of the customersupportbackoffice CancelOrderAction
 * <p>
 * Not allowing partial order or order entry cancellations as not supported
 * by Adyen
 */
public class AdyenCancelOrderAction extends CancelOrderAction {

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean canPerform(final ActionContext<OrderModel> actionContext) {
        OrderModel order = actionContext.getData();
        return order != null && CollectionUtils.isNotEmpty(order.getEntries()) &&
                CollectionUtils.isNotEmpty(order.getPaymentTransactions()) && order.getPaymentTransactions().size() == 1 &&
                getOrderCancelService().isCancelPossible(order, getUserService().getCurrentUser(), false, false).isAllowed() &&
                !getNotCancellableOrderStatus().contains(order.getStatus());
    }
}
