package com.adyen.v6.backoffice.widgets.actions.cancel;

import com.hybris.cockpitng.actions.ActionContext;
import com.hybris.cockpitng.actions.ActionResult;
import com.hybris.cockpitng.actions.CockpitAction;
import de.hybris.platform.core.model.order.OrderModel;
import org.apache.commons.collections4.CollectionUtils;

/**
 * Adyen cancel order action
 * <p>
 * Not allowing partial order or order entry cancellations as not supported
 * by Adyen
 */
public class AdyenCancelOrderAction implements CockpitAction<OrderModel, OrderModel> {

    @Override
    public boolean canPerform(final ActionContext<OrderModel> actionContext) {
        OrderModel order = actionContext.getData();
        return order != null && CollectionUtils.isNotEmpty(order.getEntries()) &&
                CollectionUtils.isNotEmpty(order.getPaymentTransactions()) && order.getPaymentTransactions().size() == 1;
    }

    @Override
    public ActionResult<OrderModel> perform(ActionContext<OrderModel> actionContext) {
        // Basic implementation - can be enhanced as needed
        return new ActionResult<>(ActionResult.SUCCESS, actionContext.getData());
    }

    @Override
    public boolean needsConfirmation(ActionContext<OrderModel> actionContext) {
        return true;
    }

    @Override
    public String getConfirmationMessage(ActionContext<OrderModel> actionContext) {
        return "Are you sure you want to cancel this order?";
    }
}
