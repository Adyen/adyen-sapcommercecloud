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
    /**
     * Persists Adyen's response metadata - the stored token and the network transaction id among it - on
     * the order's PaymentInfo.
     *
     * <p>Typed on AbstractOrderModel rather than OrderModel because it is also called with the session cart,
     * before an order exists: the PaymentInfo that the place-order strategy carries over has to hold the
     * token already, or anything reacting to the order being placed finds none.</p>
     *
     * <p>This is the only method to implement. It used to be an overload pair, which meant every caller and
     * every Mockito verification silently chose between two methods on the static type of its argument, and
     * two implementations had to be kept in step.</p>
     */
    void updatePaymentInfo(AbstractOrderModel order, String paymentMethodType, Map<String, String> additionalData);

    /**
     * @deprecated use {@link #updatePaymentInfo(AbstractOrderModel, String, Map)}. Kept as a forwarding
     *             default so callers compiled against the older signature still link; it adds nothing, and
     *             new code that binds to it only makes the call harder to follow.
     */
    @Deprecated
    default void updatePaymentInfo(OrderModel order, String paymentMethodType, Map<String, String> additionalData) {
        updatePaymentInfo((AbstractOrderModel) order, paymentMethodType, additionalData);
    }

    FraudReportModel createFraudReportFromPaymentsResponse(String pspReference,  FraudResult fraudResult );

    void storeFraudReport(FraudReportModel fraudReport);

    void storeFraudReport(OrderModel order, String pspreference, FraudResult fraudResult);

}
