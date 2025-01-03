package com.adyen.commerce.dto;

import com.adyen.model.checkout.PaymentResponse;
import de.hybris.platform.commercefacades.order.data.OrderData;

public class OrderPaymentResult {
    private OrderData orderData;
    private PaymentResponse paymentResponse;

    public OrderPaymentResult(OrderData orderData, PaymentResponse paymentResponse) {
        this.orderData = orderData;
        this.paymentResponse = paymentResponse;
    }

    public OrderData getOrderData() {
        return orderData;
    }

    public PaymentResponse getPaymentResponse() {
        return paymentResponse;
    }
}
