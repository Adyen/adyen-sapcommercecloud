package com.adyen.v6.listeners;

import com.adyen.v6.events.AuthorisationEvent;
import com.adyen.v6.model.AdyenNotificationModel;
import de.hybris.platform.payment.model.PaymentTransactionModel;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.log4j.Logger;

import java.util.Date;

public class AuthorisationNotificationEventListener extends AbstractNotificationEventListener<AuthorisationEvent> {


    private static final Logger LOG = Logger.getLogger(AuthorisationNotificationEventListener.class);

    public AuthorisationNotificationEventListener() {
        super();
    }

    @Override
    protected void onEvent(final AuthorisationEvent event) {
        final AdyenNotificationModel notification =
                event.getNotificationRequestItem();

        final PaymentTransactionModel existingTransaction =
                getPaymentTransactionRepository()
                        .getTransactionModel(notification.getPspReference());

        try {
            final boolean failedAuthorisation =
                    !BooleanUtils.isTrue(notification.getSuccess());

            if (existingTransaction == null || failedAuthorisation) {
                getAdyenNotificationService()
                        .processAuthorisationEvent(notification);
            } else {
                LOG.warn("Authorisation already processed "
                        + existingTransaction.getRequestId());
            }

            notification.setProcessedAt(new Date());
            getModelService().save(notification);

        } catch (final Exception e) {
            logException(notification, e, LOG);
        }
    }

}
