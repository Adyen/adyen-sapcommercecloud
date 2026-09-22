package com.adyen.v6.backoffice.widgets.actions.cancel;

import com.hybris.cockpitng.actions.ActionContext;
import com.hybris.cockpitng.actions.ActionResult;
import com.hybris.cockpitng.actions.CockpitAction;
import de.hybris.platform.core.enums.OrderStatus;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.ordercancel.OrderCancelRequest;
import de.hybris.platform.ordercancel.OrderCancelService;
import de.hybris.platform.servicelayer.user.UserService;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.log4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Adyen cancel order action
 * <p>
 * Not allowing partial order or order entry cancellations as not supported
 * by Adyen
 */
public class AdyenCancelOrderAction implements CockpitAction<OrderModel, OrderModel> {

    private static final Logger LOG = Logger.getLogger(AdyenCancelOrderAction.class);

    @Resource
    private OrderCancelService orderCancelService;

    @Resource
    private UserService userService;

    @Override
    public boolean canPerform(final ActionContext<OrderModel> actionContext) {
        final OrderModel order = actionContext.getData();

        return order != null
                && CollectionUtils.isNotEmpty(order.getEntries())
                && CollectionUtils.isNotEmpty(order.getPaymentTransactions())
                && order.getPaymentTransactions().size() == 1
                && !getNotCancellableOrderStatus().contains(order.getStatus())
                && orderCancelService.isCancelPossible(order, userService.getCurrentUser(), false, false).isAllowed();
    }

    @Override
    public ActionResult<OrderModel> perform(final ActionContext<OrderModel> actionContext) {
        final OrderModel order = actionContext.getData();

        if (order == null) {
            return new ActionResult<>(ActionResult.ERROR);
        }

        try {
            // Perform a FULL order cancellation.
            // Adyen typically does not support partial void/captures via the standard plugin simple flow.
            OrderCancelRequest request = new OrderCancelRequest(order);

            // Execute the cancellation
            orderCancelService.requestOrderCancel(request, userService.getCurrentUser());

            LOG.info("Full order cancellation triggered for Order: " + order.getCode());
            return new ActionResult<>(ActionResult.SUCCESS, order);

        } catch (Exception e) {
            LOG.error("Failed to cancel order: " + order.getCode(), e);
            return new ActionResult<>(ActionResult.ERROR, order);
        }
    }

    @Override
    public boolean needsConfirmation(final ActionContext<OrderModel> actionContext) {
        return true;
    }

    @Override
    public String getConfirmationMessage(final ActionContext<OrderModel> actionContext) {
        // Simple confirmation. In a real scenario, you might want to localize this string.
        return "Are you sure you want to cancel this order? This will trigger a full refund/void via Adyen.";
    }

    /**
     * Define statuses where cancellation is explicitly blocked by Adyen logic.
     */
    protected List<OrderStatus> getNotCancellableOrderStatus() {
        return List.of(
                OrderStatus.CANCELLING,
                OrderStatus.CANCELLED,
                OrderStatus.COMPLETED
        );
    }
}