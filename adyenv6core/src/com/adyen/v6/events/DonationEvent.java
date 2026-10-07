package com.adyen.v6.events;

import com.adyen.v6.model.AdyenNotificationModel;

public class DonationEvent extends AbstractNotificationEvent {
    public DonationEvent(final AdyenNotificationModel adyenNotificationModel) {
        super(adyenNotificationModel);
    }
}
