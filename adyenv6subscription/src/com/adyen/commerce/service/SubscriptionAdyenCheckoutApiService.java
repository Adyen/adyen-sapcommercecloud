package com.adyen.commerce.service;

import com.adyen.model.checkout.PaymentResponse;
import com.adyen.service.exception.ApiException;
import de.hybris.platform.core.model.order.AbstractOrderModel;

import java.io.IOException;

public interface SubscriptionAdyenCheckoutApiService {

    PaymentResponse processPaymentRequest(final AbstractOrderModel subscriptionOrder) throws Exception;

    PaymentResponse processPaymentRequest(final AbstractOrderModel subscriptionOrder, final AbstractOrderModel onFirstBillOrder) throws Exception;
}
