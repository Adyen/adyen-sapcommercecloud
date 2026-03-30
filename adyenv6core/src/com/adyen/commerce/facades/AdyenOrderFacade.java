package com.adyen.commerce.facades;

import de.hybris.platform.core.model.order.OrderModel;

/**
 * Facade responsible for order payment status retrieval.
 */
public interface AdyenOrderFacade {

    String getPaymentStatus(final String orderCode, final String sessionGuid);

    String getPaymentStatusOCC(final String code);

    String getOrderCodeForGUID(final String orderGUID, final String sessionGuid);

    OrderModel getOrderModelForCodeOCC(String code);
}
