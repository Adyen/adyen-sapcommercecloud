package com.adyen.commerce.facades;

/**
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
}
