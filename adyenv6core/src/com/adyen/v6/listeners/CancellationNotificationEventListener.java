package com.adyen.v6.listeners;

import com.adyen.v6.events.CancellationEvent;
import com.adyen.v6.model.AdyenNotificationModel;
import de.hybris.platform.payment.model.PaymentTransactionModel;
import org.apache.log4j.Logger;

import java.util.Date;

public class CancellationNotificationEventListener extends AbstractNotificationEventListener<CancellationEvent> {

    private static final Logger LOG = Logger.getLogger(CancellationNotificationEventListener.class);

    public CancellationNotificationEventListener() {
        super();
    }

    @Override
    protected void onEvent(final CancellationEvent event) {
        AdyenNotificationModel notificationInfoModel = event.getNotificationRequestItem();
        PaymentTransactionModel transactionModel = getPaymentTransactionRepository().getTransactionModel(notificationInfoModel.getOriginalReference());
        try {
            getAdyenNotificationService().processCancelEvent(notificationInfoModel, transactionModel);
            LOG.info("Cancellation notification with PSPReference " + notificationInfoModel.getPspReference() + " was processed");
            notificationInfoModel.setProcessedAt(new Date());
            getModelService().save(notificationInfoModel);
        } catch (Exception e) {
            logException(notificationInfoModel, e, LOG);
        }
    }
}
