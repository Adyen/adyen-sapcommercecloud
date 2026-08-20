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

import com.adyen.model.checkout.FraudResult;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.fraud.model.FraudReportModel;

import java.util.Map;

public interface AdyenOrderService {
    void updatePaymentInfo(OrderModel order, String paymentMethodType, Map<String, String> additionalData);

    /**
     * Pre-place-order variant used to persist Adyen response metadata on a cart. Kept as a default
     * method so existing third-party implementations of this public service remain binary compatible.
     */
    default void updatePaymentInfo(AbstractOrderModel order, String paymentMethodType, Map<String, String> additionalData) {
        if (order instanceof OrderModel) {
            updatePaymentInfo((OrderModel) order, paymentMethodType, additionalData);
            return;
        }
        throw new UnsupportedOperationException("This AdyenOrderService does not support cart payment-info updates");
    }

    FraudReportModel createFraudReportFromPaymentsResponse(String pspReference,  FraudResult fraudResult );

    void storeFraudReport(FraudReportModel fraudReport);

    void storeFraudReport(OrderModel order, String pspreference, FraudResult fraudResult);

}
