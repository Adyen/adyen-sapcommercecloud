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
 *  Copyright (c) 2026 Adyen B.V.
 *  This file is open source and available under the MIT license.
 *  See the LICENSE file for more info.
 */
package com.adyen.v6.service;

import de.hybris.platform.core.model.order.AbstractOrderModel;

/**
 * An extension point that can refuse a payment before anything is sent to Adyen: before {@code /payments}, and
 * before the partial payment order that precedes a gift card being charged. Another extension contributes a guard
 * by adding it to the {@code adyenPaymentAuthorisationGuards} list with a {@code ListMergeDirective}; the list is
 * empty by default, and then nothing changes.
 *
 * <p>Not consulted when the shopper returns from 3DS or a redirect (the payment has been authorised by then).</p>
 */
public interface PaymentAuthorisationGuard
{
    /**
     * @param cart  the cart being paid for, never {@code null}
     * @param stage where in the checkout the payment is
     * @throws PaymentAuthorisationRefusedException to refuse; the payment is then not attempted
     */
    void beforeAuthorisation(AbstractOrderModel cart, PaymentAuthorisationStage stage);
}
