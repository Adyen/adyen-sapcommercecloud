/*
 *                        ######
 *                        ######
 *  ############    ####( ######  #####. ######  ############   ############
 *  #############  #####( ######  #####. ######  #############  #############
 *         ######  #####( ######  #####. ######  #####  ######  #####  ######
 *  ###### ######  #####( ######  #####. ######  #####  #####   #####  ######
 *  ###### ######  #####( ######  #####. ######  #####          #####  ######
 *  #############  #############  #############  #############  #####  ######
 *   ############   ############  #############   ############  #####  ######
 *                                       ######
 *                                #############
 *                                ############
 *
 *  Adyen Hybris Extension
 *
 *  Copyright (c) 2017 Adyen B.V.
 *  This file is open source and available under the MIT license.
 *  See the LICENSE file for more info.
 */
package com.adyen.commerce.services;

import com.adyen.model.checkout.PaymentMethod;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service responsible for filtering and extracting configuration from Adyen payment methods.
 * Also handles locale resolution for Amazon Pay.
 */
public interface AdyenPaymentMethodConfigService {

    /**
     * Returns the list of payment method types to exclude, read from configuration.
     */
    List<String> getExcludedPaymentMethods();

    /**
     * Returns the list of allowed payment method types, read from configuration.
     * An empty list means all methods are allowed.
     */
    List<String> getAllowedPaymentMethods();

    /**
     * Extracts the Apple Pay configuration map from the given payment methods list.
     *
     * @param paymentMethods the list of available payment methods
     * @return Apple Pay configuration map, or an empty map if not present
     */
    Map<String, String> getApplePayConfig(List<PaymentMethod> paymentMethods);

    /**
     * Extracts the Google Pay configuration map from the given payment methods list.
     *
     * @param paymentMethods the list of available payment methods
     * @return Google Pay configuration map, or an empty map if not present
     */
    Map<String, String> getGooglePayConfig(List<PaymentMethod> paymentMethods);

    /**
     * Extracts the PayPal configuration map from the given payment methods list.
     *
     * @param paymentMethods the list of available payment methods
     * @return PayPal configuration map, or an empty map if not present
     */
    Map<String, String> getPayPalConfig(List<PaymentMethod> paymentMethods);

    /**
     * Finds the Amazon Pay payment method from the given list, if present.
     *
     * @param paymentMethods the list of available payment methods
     * @return an Optional containing the Amazon Pay method, or empty
     */
    Optional<PaymentMethod> getAmazonPayMethod(List<PaymentMethod> paymentMethods);

    /**
     * Returns whether the given payment method should be hidden from the checkout UI.
     * Hidden methods include scheme (cards), boleto, SEPA, WeChat Pay (non-web), and issuer methods.
     *
     * @param paymentMethod the payment method to check
     * @return true if the method should be hidden
     */
    boolean isHiddenPaymentMethod(PaymentMethod paymentMethod);

    /**
     * Resolves the Amazon Pay locale string based on the Amazon Pay configuration and the shopper locale.
     *
     * @param amazonPayConfig the Amazon Pay configuration map (may be null)
     * @param shopperLocale   the current shopper locale ISO code
     * @return the resolved locale string (e.g. "en_US", "de_DE")
     */
    String resolveAmazonPayLocale(Map<String, String> amazonPayConfig, String shopperLocale);
}
