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

import com.adyen.httpclient.HTTPClientException;
import com.adyen.model.checkout.Amount;
import com.adyen.model.checkout.CreateCheckoutSessionResponse;
import com.adyen.model.checkout.PaymentCompletionDetails;
import com.adyen.model.checkout.PaymentDetailsRequest;
import com.adyen.model.checkout.PaymentDetailsResponse;
import com.adyen.model.checkout.PaymentMethod;
import com.adyen.model.checkout.PaymentMethodsResponse;
import com.adyen.model.checkout.PaymentRequest;
import com.adyen.model.checkout.PaymentResponse;
import com.adyen.model.recurring.RecurringDetail;
import com.adyen.model.terminal.ConnectedTerminalsResponse;
import com.adyen.model.terminal.TerminalAPIResponse;
import com.adyen.service.exception.ApiException;
import com.adyen.v6.model.RequestInfo;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.core.model.user.CustomerModel;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.SignatureException;
import java.util.List;

public interface AdyenCheckoutApiService {

    ConnectedTerminalsResponse getConnectedTerminals() throws IOException, ApiException;

    PaymentResponse processPaymentRequest(CartData cartData, PaymentRequest originPaymentsRequest, RequestInfo requestInfo, CustomerModel customerModel) throws Exception;

    PaymentDetailsResponse authorise3DSPayment(PaymentDetailsRequest paymentsDetailsRequest) throws Exception;

    /**
     * Get payment methods using /paymentMethods - Checkout API
     */
    List<PaymentMethod> getPaymentMethods(BigDecimal amount, String currency, String countryCode, String shopperLocale, String shopperReference) throws IOException, ApiException;

    PaymentMethodsResponse getPaymentMethodsResponse(BigDecimal amount, String currency, String countryCode, String shopperLocale, String shopperReference) throws IOException, ApiException;

    PaymentMethodsResponse getPaymentMethodsResponse(BigDecimal amount, String currency, String countryCode, String shopperLocale, String shopperReference, List<String> blockedPaymentMethods) throws IOException, ApiException;

    /**
     * @deprecated use getPaymentMethods including shopperReference instead {@link #getPaymentMethods(BigDecimal amount, String currency, String countryCode, String shopperLocale, String shopperReference)
     */
    @Deprecated
    List<PaymentMethod> getPaymentMethods(BigDecimal amount, String currency, String countryCode, String shopperLocale) throws HTTPClientException, SignatureException, IOException;

    /**
     * Retrieve stored cards from recurring contracts via Adyen API
     *
     * @deprecated use getPaymentMethodsResponse instead {@link #getPaymentMethodsResponse(BigDecimal amount, String currency, String countryCode, String shopperLocale, String shopperReference)} ()
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

    /**
     * Send POS Payment Request using Adyen Terminal API
     */
    TerminalAPIResponse sendSyncPosPaymentRequest(CartData cartData, CustomerModel customer, String serviceId) throws Exception;

    /**
     * Send POS Status Request using Adyen Terminal API
     */
    TerminalAPIResponse sendSyncPosStatusRequest(CartData cartData, String serviceId) throws Exception;

    CreateCheckoutSessionResponse getPaymentSessionData(final CartData cartData) throws IOException, ApiException;

    CreateCheckoutSessionResponse getPaymentSessionData(final Amount amount) throws IOException, ApiException;
}
