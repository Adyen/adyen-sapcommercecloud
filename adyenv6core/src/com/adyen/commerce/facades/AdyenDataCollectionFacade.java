package com.adyen.commerce.facades;

<<<<<<<< HEAD:adyenv6core/src/com/adyen/commerce/facades/AdyenDataCollectionFacade.java
import com.adyen.commerce.data.DataCollectionConfiguration;

/**
 * Facade responsible for providing data collection configuration.
 */
public interface AdyenDataCollectionFacade {

    DataCollectionConfiguration getDataCollectionConfiguration();
========
/**
 * @deprecated Use {@link com.adyen.commerce.facades.AdyenDataCollectionFacade} instead.
 *             This interface will be removed in a future release.
 */
@Deprecated(since = "2.x", forRemoval = true)
public interface AdyenDataCollectionFacade extends com.adyen.commerce.facades.AdyenDataCollectionFacade {
    // All methods inherited from com.adyen.commerce.facades.AdyenDataCollectionFacade
>>>>>>>> feature/AD-489_fixed:adyenv6core/src/com/adyen/v6/facades/AdyenDataCollectionFacade.java
}
