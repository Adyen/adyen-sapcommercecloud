package com.adyen.v6.events.builder;

import com.adyen.v6.events.AbstractNotificationEvent;
import com.adyen.v6.events.CancellationEvent;
import com.adyen.v6.model.AdyenNotificationModel;

public class CancellationEventBuilder extends AbstractNotificationEventBuilder {
    @Override
    public AbstractNotificationEvent buildEvent(AdyenNotificationModel adyenNotificationModel) {
        return new CancellationEvent(adyenNotificationModel);
    }
}
