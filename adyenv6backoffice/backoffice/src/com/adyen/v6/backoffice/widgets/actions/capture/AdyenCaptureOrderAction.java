package com.adyen.v6.backoffice.widgets.actions.capture;

import com.adyen.service.exception.ApiException;
import com.adyen.v6.factory.AdyenPaymentServiceFactory;
import com.adyen.v6.service.AdyenModificationsApiService;
import com.adyen.v6.service.AdyenTransactionService;
import com.hybris.backoffice.widgets.notificationarea.event.NotificationEvent;
import com.hybris.cockpitng.actions.ActionContext;
import com.hybris.cockpitng.actions.ActionResult;
import com.hybris.cockpitng.actions.CockpitAction;
import com.hybris.cockpitng.util.notifications.NotificationService;
import com.hybris.cockpitng.util.notifications.event.NotificationEventTypes;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.promotions.backoffice.actions.CalculateWithPromotionsAction;
import de.hybris.platform.promotions.backoffice.constants.PromotionsbackofficeConstants;
import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.store.BaseStoreModel;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.util.Currency;

public class AdyenCaptureOrderAction implements CockpitAction<OrderModel, OrderModel> {
    private static final Logger LOG = Logger.getLogger(AdyenCaptureOrderAction.class);

    private static final String CONFIRMATION_MESSAGE = "adyen.order.capture.confirmation.message";
    private static final String CAPTURE_MESSAGE_SOURCE = "adyen-capture-message-source";


    @Autowired
    private NotificationService notificationService;

    @Autowired
    private AdyenPaymentServiceFactory adyenPaymentServiceFactory;


    @Override
    public boolean needsConfirmation(ActionContext<OrderModel> context) {
        return context.getData() != null;
    }

    @Override
    public String getConfirmationMessage(ActionContext<OrderModel> context) {
        return context.getLabel(CONFIRMATION_MESSAGE);
    }

    @Override
    public ActionResult<OrderModel> perform(ActionContext<OrderModel> actionContext) {
        OrderModel orderModel = actionContext.getData();

        if (orderModel == null) {
            LOG.error("Null order model");
            notificationService.notifyUser(CAPTURE_MESSAGE_SOURCE,
                    NotificationEventTypes.EVENT_TYPE_GENERAL, NotificationEvent.Level.FAILURE);

            return new ActionResult(ActionResult.ERROR);
        }

        try {
            BaseStoreModel baseStore = orderModel.getStore();
            Assert.notNull(baseStore, "BaseStore model is null");
            AdyenModificationsApiService adyenPaymentService = adyenPaymentServiceFactory.createAdyenModificationsApiService(baseStore);

            BigDecimal totalPrice = BigDecimal.valueOf(orderModel.getTotalPrice());
            Currency currency = Currency.getInstance(orderModel.getCurrency().getIsocode());
            String pspReference = AdyenTransactionService.getPspReferenceForOrder(orderModel);

            LOG.debug("Capturing order " + orderModel.getCode() + " psp reference " + pspReference);


            adyenPaymentService.capture(totalPrice, currency, pspReference, orderModel.getCode());
        } catch (Exception e) {
            if(e instanceof ApiException) {
                LOG.error("Adyen API Exception: " + ((ApiException) e).getResponseBody());
            }
            LOG.error("Error capturing order " + orderModel.getCode(), e);
            notificationService.notifyUser(CAPTURE_MESSAGE_SOURCE,
                    NotificationEventTypes.EVENT_TYPE_GENERAL, NotificationEvent.Level.FAILURE);

            return new ActionResult(ActionResult.ERROR);
        }


        notificationService.notifyUser(CAPTURE_MESSAGE_SOURCE,
                NotificationEventTypes.EVENT_TYPE_GENERAL, NotificationEvent.Level.SUCCESS);

        return new ActionResult(ActionResult.SUCCESS, orderModel);
    }

    @Override
    public boolean canPerform(ActionContext<OrderModel> actionContext) {
        OrderModel orderModel = actionContext.getData();
        if (orderModel == null) {
            return false;
        }

        return AdyenTransactionService.isOrderAuthorized(orderModel);
    }
}
