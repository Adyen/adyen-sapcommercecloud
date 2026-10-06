package com.adyen.v6.events.builder;

import com.adyen.v6.events.AbstractNotificationEvent;
import com.adyen.v6.events.DonationEvent;
import com.adyen.v6.model.AdyenNotificationModel;

public class DonationEventBuilder extends AbstractNotificationEventBuilder {
    @Override
    public AbstractNotificationEvent buildEvent(final AdyenNotificationModel adyenNotificationModel) {
        return new DonationEvent(adyenNotificationModel);
    }
}
