package com.adyen.v6.service;

import com.adyen.v6.model.AdyenNotificationModel;
import de.hybris.platform.core.model.order.OrderModel;

public interface AdyenPaymentOutcomeService {

    boolean markOfferClosed(
            OrderModel order,
            AdyenNotificationModel notification);

    boolean markAuthorisationFailed(
            OrderModel order,
            AdyenNotificationModel notification);
}