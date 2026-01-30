package com.adyen.commerce.service;

import de.hybris.platform.subscriptionservices.model.SubscriptionModel;

public interface RecurringOrderService {
    void createRecurringOrderForSubscription(final SubscriptionModel subscriptionModel) throws Exception;
}
