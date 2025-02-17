package com.adyen.v6.events;

import com.adyen.v6.model.AdyenNotificationModel;

public class ChargebackEvent extends AbstractNotificationEvent {
    public ChargebackEvent(AdyenNotificationModel adyenNotificationModel) {
        super(adyenNotificationModel);
    }
}
