package com.adyen.commerce.response;

import com.adyen.model.checkout.PaymentDetailsResponse;
import com.adyen.model.checkout.PaymentResponse;
import com.adyen.model.checkout.PaymentResponseAction;

public class PlaceOrderResponse {
    private String orderNumber;
    private String error;
    private boolean executeAction;
    private PaymentResponseAction paymentsAction;
    private PaymentResponse paymentsResponse;
    private PaymentDetailsResponse paymentDetailsResponse;

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public boolean isExecuteAction() {
        return executeAction;
    }

    public void setExecuteAction(boolean executeAction) {
        this.executeAction = executeAction;
    }

    public PaymentResponseAction getPaymentsAction() {
        return paymentsAction;
    }

    public void setPaymentsAction(PaymentResponseAction paymentsAction) {
        this.paymentsAction = paymentsAction;
    }

    public PaymentResponse getPaymentsResponse() {
        return paymentsResponse;
    }

    public void setPaymentsResponse(PaymentResponse paymentsResponse) {
        this.paymentsResponse = paymentsResponse;
    }

    public PaymentDetailsResponse getPaymentDetailsResponse() {
        return paymentDetailsResponse;
    }

    public void setPaymentDetailsResponse(PaymentDetailsResponse paymentDetailsResponse) {
        this.paymentDetailsResponse = paymentDetailsResponse;
    }
}
