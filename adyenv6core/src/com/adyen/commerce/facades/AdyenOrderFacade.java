package com.adyen.commerce.facades;

<<<<<<<< HEAD:adyenv6core/src/com/adyen/commerce/facades/AdyenOrderFacade.java
import de.hybris.platform.core.model.order.OrderModel;

/**
 * Facade responsible for order payment status retrieval.
 */
public interface AdyenOrderFacade {

    String getPaymentStatus(final String orderCode, final String sessionGuid);

    String getPaymentStatusOCC(final String code);

    String getOrderCodeForGUID(final String orderGUID, final String sessionGuid);

    OrderModel getOrderModelForCodeOCC(String code);
========
/**
 * @deprecated Use {@link com.adyen.commerce.facades.AdyenOrderFacade} instead.
 *             This interface will be removed in a future release.
 */
@Deprecated(since = "2.x", forRemoval = true)
public interface AdyenOrderFacade extends com.adyen.commerce.facades.AdyenOrderFacade {
    // All methods inherited from com.adyen.commerce.facades.AdyenOrderFacade
>>>>>>>> feature/AD-489_fixed:adyenv6core/src/com/adyen/v6/facades/AdyenOrderFacade.java
}
