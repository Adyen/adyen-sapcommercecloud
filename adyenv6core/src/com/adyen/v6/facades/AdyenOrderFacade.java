package com.adyen.v6.facades;

import de.hybris.platform.core.model.order.OrderModel;

public interface AdyenOrderFacade {
    String getPaymentStatus(final String orderCode, final String sessionGuid);
    String getPaymentStatusOCC(final String code);
    String getOrderCodeForGUID(final String orderGUID, final String sessionGuid);
    OrderModel getOrderModelForCodeOCC(String code);
}