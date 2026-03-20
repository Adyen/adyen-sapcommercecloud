package com.adyen.v6.events;

import com.adyen.v6.model.AdyenNotificationModel;

public class CancellationEvent extends AbstractNotificationEvent {
    public CancellationEvent(AdyenNotificationModel adyenNotificationModel) {
        super(adyenNotificationModel);
    }
}
