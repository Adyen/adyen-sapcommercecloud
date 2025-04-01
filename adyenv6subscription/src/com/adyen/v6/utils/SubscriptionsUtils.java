package com.adyen.v6.utils;

import com.adyen.model.checkout.PaymentRequest;
import de.hybris.platform.commercefacades.order.data.CartData;
import org.apache.commons.collections.CollectionUtils;

public class SubscriptionsUtils {
    public static boolean containsSubscription(CartData cartData){
            return CollectionUtils.isNotEmpty(cartData.getEntries()) && cartData.getEntries().stream().anyMatch(orderEntryData -> orderEntryData.getProduct().getSubscriptionTerm() != null);
    }

    public static PaymentRequest.RecurringProcessingModelEnum findRecurringProcessingModel(CartData cartData){
        if(cartData.getEntries().stream()
                .filter(orderEntryData->orderEntryData.getProduct().getSubscriptionTerm()!=null)
                .allMatch(orderEntryData -> orderEntryData.getProduct().getSubscriptionTerm().getBillingPlan()!=null)) {
            return PaymentRequest.RecurringProcessingModelEnum.SUBSCRIPTION;
        }

        return PaymentRequest.RecurringProcessingModelEnum.UNSCHEDULEDCARDONFILE;
    }
}
