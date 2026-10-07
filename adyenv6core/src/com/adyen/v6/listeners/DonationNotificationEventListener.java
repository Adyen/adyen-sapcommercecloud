package com.adyen.v6.listeners;

import com.adyen.v6.events.DonationEvent;
import com.adyen.v6.model.AdyenNotificationModel;
import com.adyen.v6.service.AdyenDonationReconciliationService;
import org.apache.log4j.Logger;

import java.util.Date;

public class DonationNotificationEventListener extends AbstractNotificationEventListener<DonationEvent> {
    private static final Logger LOG = Logger.getLogger(DonationNotificationEventListener.class);

    private AdyenDonationReconciliationService adyenDonationReconciliationService;

    @Override
    protected void onEvent(final DonationEvent event) {
        final AdyenNotificationModel notification = event.getNotificationRequestItem();
        try {
            adyenDonationReconciliationService.reconcile(notification);
            notification.setProcessedAt(new Date());
            getModelService().save(notification);
            LOG.info("DONATION notification with PSP reference " + notification.getPspReference() + " was processed");
        } catch (final Exception exception) {
            logException(notification, exception, LOG);
        }
    }

    public void setAdyenDonationReconciliationService(
            final AdyenDonationReconciliationService adyenDonationReconciliationService) {
        this.adyenDonationReconciliationService = adyenDonationReconciliationService;
    }
}
