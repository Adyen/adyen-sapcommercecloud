package com.adyen.commerce.service.impl;

import com.adyen.commerce.service.RecurringOrderService;
import de.hybris.platform.commerceservices.impersonation.ImpersonationContext;
import de.hybris.platform.commerceservices.impersonation.ImpersonationService;
import de.hybris.platform.core.Registry;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.subscriptionservices.model.SubscriptionModel;

public class DefaultRecurringOrderService implements RecurringOrderService {

    private ImpersonationService impersonationService;


    public void createRecurringOrderForSubscription(final SubscriptionModel subscriptionModel) throws Exception
    {
        OrderModel originalOrder = subscriptionModel.getSubscriptionOrder();
        final ImpersonationService.Executor<Void, Exception> executor = (ImpersonationService.Executor<Void, Exception>) Registry
                .getApplicationContext()
                .getBean(SubscriptionOrderExecutor.BEAN_NAME, subscriptionModel);

        final ImpersonationContext context = new ImpersonationContext();
        context.setSite(originalOrder.getSite());
        context.setCurrency(originalOrder.getCurrency());
        context.setUser(originalOrder.getUser());
        context.setLanguage(originalOrder.getLanguage());
        impersonationService.executeInContext(context, executor);
    }

    public void setImpersonationService(ImpersonationService impersonationService) {
        this.impersonationService = impersonationService;
    }
}
