package com.adyen.commerce.service;

import com.adyen.model.checkout.PaymentResponse;
import com.adyen.service.exception.ApiException;
import de.hybris.platform.core.model.order.AbstractOrderModel;

import java.io.IOException;

public interface SubscriptionAdyenCheckoutApiService {

    PaymentResponse processSubscriptionPaymentRequest(final AbstractOrderModel subscriptionOrder) throws IOException, ApiException;

    PaymentResponse processSubscriptionPaymentRequest(final AbstractOrderModel subscriptionOrder, final AbstractOrderModel onFirstBillOrder) throws IOException, ApiException;

    PaymentResponse processOneTimePaymentRequest(final AbstractOrderModel subscriptionOrder) throws IOException, ApiException;
}
