package com.adyen.commerce.dto;

import com.adyen.model.checkout.PaymentDetailsResponse;
import com.adyen.model.checkout.PaymentResponse;
import de.hybris.platform.commercefacades.order.data.OrderData;

public class OrderPaymentResult {
    private OrderData orderData;
    private PaymentResponse paymentResponse;
    private PaymentDetailsResponse paymentDetailsResponse;

    public OrderPaymentResult(OrderData orderData, PaymentResponse paymentResponse) {
        this.orderData = orderData;
        this.paymentResponse = paymentResponse;
    }

    public OrderPaymentResult(OrderData orderData, PaymentDetailsResponse paymentDetailsResponse) {
        this.orderData = orderData;
        this.paymentDetailsResponse = paymentDetailsResponse;
    }

    public OrderData getOrderData() {
        return orderData;
    }

    public PaymentResponse getPaymentResponse() {
        return paymentResponse;
    }

    public PaymentDetailsResponse getPaymentDetailsResponse() {
        return paymentDetailsResponse;
    }
}
