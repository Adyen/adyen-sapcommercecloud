package com.adyen.v6.listeners;

import com.adyen.v6.events.ChargebackEvent;
import com.adyen.v6.events.RefundEvent;
import com.adyen.v6.model.AdyenNotificationModel;
import org.apache.log4j.Logger;

import java.util.Date;

public class ChargebackNotificationEventListener extends AbstractNotificationEventListener<ChargebackEvent> {


    private static final Logger LOG = Logger.getLogger(ChargebackNotificationEventListener.class);

    public ChargebackNotificationEventListener() {
        super();
    }

    @Override
    protected void onEvent(final ChargebackEvent event) {
        AdyenNotificationModel notificationInfoModel = event.getNotificationRequestItem();
        try {
            getAdyenNotificationService().processChargebackEvent(notificationInfoModel);
            LOG.info("Chargeback notification with PSPReference " + notificationInfoModel.getPspReference() + " was processed");
            notificationInfoModel.setProcessedAt(new Date());
            getModelService().save(notificationInfoModel);
        } catch (Exception e) {
            logException(notificationInfoModel, e, LOG);
        }
    }

}
