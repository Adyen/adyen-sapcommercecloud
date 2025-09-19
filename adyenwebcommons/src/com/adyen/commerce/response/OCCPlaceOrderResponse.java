package com.adyen.commerce.response;

import com.adyen.model.checkout.PaymentResponse;
import de.hybris.platform.commercefacades.order.data.OrderData;
import java.math.BigDecimal;

public class OCCPlaceOrderResponse extends PlaceOrderResponse {
    private OrderData orderData;
    private boolean partialPaymentProcessed;
    private BigDecimal remainingAmount;

    public OrderData getOrderData() {
        return orderData;
    }

    public void setOrderData(OrderData orderData) {
        this.orderData = orderData;
    }

    public boolean isPartialPaymentProcessed() {
        return partialPaymentProcessed;
    }

    public void setPartialPaymentProcessed(boolean partialPaymentProcessed) {
        this.partialPaymentProcessed = partialPaymentProcessed;
    }

    public BigDecimal getRemainingAmount() {
        return remainingAmount;
    }

    public void setRemainingAmount(BigDecimal remainingAmount) {
        this.remainingAmount = remainingAmount;
    }
}
