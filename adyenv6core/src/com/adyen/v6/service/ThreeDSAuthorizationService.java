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
 *  Adyen SAP Commerce Extension
 *
 *  Copyright (c) 2025 Adyen B.V.
 *  This file is open source and available under the MIT license.
 *  See the LICENSE file for more info.
 */
package com.adyen.v6.service;

import com.adyen.model.checkout.PaymentDetailsRequest;
import com.adyen.model.checkout.PaymentDetailsResponse;
import de.hybris.platform.commercefacades.order.data.OrderData;
import de.hybris.platform.core.model.order.OrderModel;

/**
 * Service responsible for handling 3D Secure (3DS) authorization processes.
 * This service encapsulates all 3DS-related functionality including:
 * - Processing 3DS authentication responses
 * - Managing 3DS session tokens
 * - Handling order status updates after 3DS completion
 */
public interface ThreeDSAuthorizationService {

    /**
     * Handles the 3DS response from the payment provider and processes the authorization.
     * This method processes the 3DS authentication result, updates the order status,
     * and manages the payment transaction accordingly.
     *
     * @param paymentsDetailsRequest the payment details request containing 3DS response data
     * @return OrderData representing the processed order
     * @throws Exception if the 3DS authorization fails or encounters an error
     */
    OrderData handle3DSResponse(PaymentDetailsRequest paymentsDetailsRequest) throws Exception;

    /**
     * Authorizes a 3DS payment by sending the payment details to Adyen.
     * This method communicates with Adyen's API to complete the 3DS authentication process.
     *
     * @param paymentsDetailsRequest the payment details request for 3DS authorization
     * @return PaymentDetailsResponse containing the authorization result
     * @throws Exception if the API call fails or returns an error
     */
    PaymentDetailsResponse authorize3DSPayment(PaymentDetailsRequest paymentsDetailsRequest) throws Exception;

    /**
     * Updates the order payment status and information based on the 3DS payment response.
     * This method handles the business logic for updating order status, creating payment transactions,
     * and triggering business processes based on the payment result.
     *
     * @param orderModel the order to update
     * @param paymentDetailsResponse the payment response from 3DS authorization
     */
    void updateOrderPaymentStatusAndInfo(OrderModel orderModel, PaymentDetailsResponse paymentDetailsResponse);

    /**
     * Clears 3DS-related session tokens and attributes.
     * This method removes fingerprint tokens, challenge tokens, and other 3DS-specific
     * session data to ensure clean session state.
     */
    void clear3DSSessionTokens();

    /**
     * Retrieves a pending order by its order code and cleans up 3DS session data.
     * This method is used during 3DS flow to retrieve the order that was placed
     * in pending state while waiting for 3DS authentication.
     *
     * @param orderCode the order code to retrieve
     * @return OrderModel the retrieved pending order
     * @throws Exception if the order cannot be found or retrieved
     */
    OrderModel retrievePendingOrderAndClear3DSSession(String orderCode) throws Exception;
}