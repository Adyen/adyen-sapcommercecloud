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
package com.adyen.v6.service;

import com.adyen.commerce.data.AdyenPartialPaymentOrderData;
import com.adyen.httpclient.HTTPClientException;
import com.adyen.model.checkout.*;
import com.adyen.model.recurring.RecurringDetail;
import com.adyen.service.exception.ApiException;
import com.adyen.v6.model.RequestInfo;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.core.model.user.CustomerModel;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.SignatureException;
import java.util.List;

public interface AdyenCheckoutApiService {


    PaymentResponse processPaymentRequest(CartData cartData, PaymentRequest originPaymentsRequest, RequestInfo requestInfo, CustomerModel customerModel) throws Exception;


    default PaymentResponse processPaymentRequest(CartData cartData, PaymentRequest originPaymentsRequest, RequestInfo requestInfo, CustomerModel customerModel, AdyenPartialPaymentOrderData partialPaymentOrderData) throws Exception {
        throw new UnsupportedOperationException("Partial payment processing not supported by this implementation");
    }


    /**
     * Process partial payment request with custom amount for gift card scenarios
     */
    default PaymentResponse processPartialPaymentRequest(CartData cartData, PaymentRequest originPaymentsRequest, RequestInfo requestInfo, CustomerModel customerModel, BigDecimal customAmount, String currency) throws Exception {
        throw new UnsupportedOperationException("Partial payment processing not supported by this implementation");
    }

    PaymentResponse sendPaymentRequest(final PaymentRequest paymentRequest, final RequestInfo requestInfo) throws IOException, ApiException;

    PaymentDetailsResponse authorise3DSPayment(PaymentDetailsRequest paymentsDetailsRequest) throws Exception;

    /**
     * Get payment methods using /paymentMethods - Checkout API
     */
    List<PaymentMethod> getPaymentMethods(BigDecimal amount, String currency, String countryCode, String shopperLocale, String shopperReference, String shopperConversionId) throws IOException, ApiException;

    PaymentMethodsResponse getPaymentMethodsResponse(BigDecimal amount, String currency, String countryCode, String shopperLocale, String shopperReference, String shopperConversionId) throws IOException, ApiException;

    PaymentMethodsResponse getPaymentMethodsResponse(BigDecimal amount, String currency, String countryCode, String shopperLocale, String shopperReference, List<String> blockedPaymentMethods, List<String> allowedPaymentMethods, String shopperConversionId) throws IOException, ApiException;

    /**
     * @deprecated use getPaymentMethods including shopperReference instead {@link #getPaymentMethods(BigDecimal amount, String currency, String countryCode, String shopperLocale, String shopperReference, , String shopperConversionId)
     */
    @Deprecated
    List<PaymentMethod> getPaymentMethods(BigDecimal amount, String currency, String countryCode, String shopperLocale) throws HTTPClientException, SignatureException, IOException;

    /**
     * Retrieve stored cards from recurring contracts via Adyen API
     *
     * @deprecated use getPaymentMethodsResponse instead {@link #getPaymentMethodsResponse(BigDecimal amount, String currency, String countryCode, String shopperLocale, String shopperReference, String shopperConversionId)} ()
     */
    @Deprecated
    List<RecurringDetail> getStoredCards(String customerId) throws IOException, ApiException;

    /**
     * Disables a recurring contract via Adyen API
     */
    boolean disableStoredCard(String customerId, String recurringReference) throws IOException, ApiException;

    /**
     * Retrieves payment response from /payments/details for redirect methods like klarna
     */
    PaymentDetailsResponse getPaymentDetailsFromPayload(PaymentDetailsRequest detailsRequest) throws Exception;

    /**
     * Retrieves payment response from /payments/details
     */
    PaymentDetailsResponse getPaymentDetailsFromPayload(PaymentCompletionDetails details) throws Exception;

    /**
     * Returns the Device Fingerprint url
     */
    String getDeviceFingerprintUrl();

}
