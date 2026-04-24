package com.adyen.commerce.facades;

/**
<<<<<<<< HEAD:adyenv6core/src/com/adyen/commerce/facades/AdyenAmazonPayFacade.java
 * Facade responsible for any direct Amazon Pay interaction logic.
 */
public interface AdyenAmazonPayFacade {

    /**
     * Returns the Amazon Pay token for an already-created checkout session.
     *
     * @param amazonpayCheckoutSessionId the previously created checkout session
     * @return the amazonPayToken related with the amazonPay session
     */
    String getAmazonPayToken(final String amazonpayCheckoutSessionId);

    /**
     * Resolves the full return URL for the Amazon Pay controller by site.
     *
     * @param url the relative url
     * @return the complete absolute url
     */
    String getReturnUrl(final String url);
========
 * @deprecated Use {@link com.adyen.commerce.facades.AdyenAmazonPayFacade} instead.
 *             This interface will be removed in a future release.
 */
@Deprecated(since = "2.x", forRemoval = true)
public interface AdyenAmazonPayFacade extends com.adyen.commerce.facades.AdyenAmazonPayFacade {
    // All methods inherited from com.adyen.commerce.facades.AdyenAmazonPayFacade
>>>>>>>> feature/AD-489_fixed:adyenv6core/src/com/adyen/v6/facades/AdyenAmazonPayFacade.java
}
