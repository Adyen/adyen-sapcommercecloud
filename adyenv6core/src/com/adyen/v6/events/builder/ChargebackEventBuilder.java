package com.adyen.v6.events.builder;

import com.adyen.v6.events.AbstractNotificationEvent;
import com.adyen.v6.events.ChargebackEvent;
import com.adyen.v6.events.RefundEvent;
import com.adyen.v6.model.AdyenNotificationModel;

public class ChargebackEventBuilder extends AbstractNotificationEventBuilder{
    @Override
    public AbstractNotificationEvent buildEvent(AdyenNotificationModel adyenNotificationModel) {
        return new ChargebackEvent(adyenNotificationModel);
    }
}
