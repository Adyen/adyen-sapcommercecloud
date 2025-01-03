package com.adyen.commerce.response;

import com.adyen.model.checkout.PaymentResponse;
import de.hybris.platform.commercefacades.order.data.OrderData;

public class OCCPlaceOrderResponse extends PlaceOrderResponse {
    private OrderData orderData;

    public OrderData getOrderData() {
        return orderData;
    }

    public void setOrderData(OrderData orderData) {
        this.orderData = orderData;
    }
}
