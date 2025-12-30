package com.adyen.commerce.service;

import com.adyen.model.checkout.PaymentResponse;
import de.hybris.platform.core.model.order.AbstractOrderModel;

public interface SubscriptionAdyenCheckoutApiService {

    PaymentResponse processSubscriptionPaymentRequest(final AbstractOrderModel subscriptionOrder) throws Exception;

    PaymentResponse processSubscriptionPaymentRequest(final AbstractOrderModel subscriptionOrder, final AbstractOrderModel onFirstBillOrder) throws Exception;

    PaymentResponse processOneTimePaymentRequest(final AbstractOrderModel subscriptionOrder) throws Exception;
}
