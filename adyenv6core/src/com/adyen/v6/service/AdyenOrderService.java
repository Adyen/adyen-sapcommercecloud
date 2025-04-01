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
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.fraud.model.FraudReportModel;

import java.util.Map;

public interface AdyenOrderService {
    void updatePaymentInfo(OrderModel order, String paymentMethodType, Map<String, String> additionalData);

    FraudReportModel createFraudReportFromPaymentsResponse(String pspReference,  FraudResult fraudResult );

    void storeFraudReport(FraudReportModel fraudReport);

    void storeFraudReport(OrderModel order, String pspreference, FraudResult fraudResult);

}
