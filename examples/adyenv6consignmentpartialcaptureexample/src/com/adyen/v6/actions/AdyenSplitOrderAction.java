package com.adyen.v6.actions;

import com.adyen.v6.actions.order.AdyenCheckNewOrderAction;
import com.adyen.v6.constants.Adyenv6consignmentpartialcaptureexampleConstants;
import com.adyen.v6.constants.Adyenv6ordermanagementConstants;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.orderprocessing.model.OrderProcessModel;
import de.hybris.platform.ordersplitting.OrderSplittingService;
import de.hybris.platform.processengine.action.AbstractSimpleDecisionAction;
import de.hybris.platform.task.RetryLaterException;
import org.apache.log4j.Logger;
import org.apache.tools.ant.util.DateUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;

public class AdyenSplitOrderAction extends AbstractSimpleDecisionAction<OrderProcessModel> {
    private static final Logger LOG = Logger.getLogger(AdyenSplitOrderAction.class);

    private OrderSplittingService orderSplittingService;

    @Override
    public Transition executeAction(OrderProcessModel orderProcessModel) throws RetryLaterException, Exception {
        if (Adyenv6consignmentpartialcaptureexampleConstants.PARTIAL_CAPTURE_DEMO) {
            LOG.info("Partial capture demo split action");

            setDifferentDeliveryDatesForEveryEntry(orderProcessModel.getOrder());

            orderSplittingService.splitOrderForConsignment(orderProcessModel.getOrder(), orderProcessModel.getOrder().getEntries());
        }

        return Transition.OK;
    }

    private void setDifferentDeliveryDatesForEveryEntry(final AbstractOrderModel orderModel){
        LocalDate date = LocalDate.now().plusDays(5);
        for (AbstractOrderEntryModel entry : orderModel.getEntries()) {
            entry.setNamedDeliveryDate(Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant()));
            date = date.plusDays(1);
        }
    }

    public void setOrderSplittingService(OrderSplittingService orderSplittingService) {
        this.orderSplittingService = orderSplittingService;
    }
}
