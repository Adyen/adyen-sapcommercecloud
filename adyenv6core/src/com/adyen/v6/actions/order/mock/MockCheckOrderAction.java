package com.adyen.v6.actions.order.mock;

import de.hybris.platform.orderprocessing.model.OrderProcessModel;
import de.hybris.platform.processengine.action.AbstractSimpleDecisionAction;
import org.apache.log4j.Logger;

public class MockCheckOrderAction extends AbstractSimpleDecisionAction<OrderProcessModel> {

    private static final Logger LOG = Logger.getLogger(MockCheckOrderAction.class);

    @Override
    public Transition executeAction(final OrderProcessModel process) {
        LOG.info("Executing MockCheckOrderAction for order process: " + process.getCode());

        // In a real CheckOrderAction, you would validate the order.
        // For this mock, we'll assume the order is always valid.
        if (process.getOrder() == null) {
            LOG.error("Order is null in process " + process.getCode() + ". Transitioning to NOK.");
            return Transition.NOK;
        }

        LOG.info("MockCheckOrderAction: Order " + process.getOrder().getCode() + " check successful (mocked). Transitioning to OK.");
        return Transition.OK;
    }
}