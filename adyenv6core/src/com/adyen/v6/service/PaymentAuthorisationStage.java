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

/**
 * Where in the checkout a {@link PaymentAuthorisationGuard} is being asked, for a guard that treats the steps
 * differently and for its log line.
 */
public enum PaymentAuthorisationStage
{
    /** The Adyen {@code /payments} request for the whole cart, or for the remaining amount after a gift card. */
    PAYMENT,

    /** The {@code /payments} request that charges a gift card, the first leg of a partial payment. */
    PARTIAL_PAYMENT,

    /** The Adyen {@code /orders} call that opens a partial payment order, ahead of the gift card being charged. */
    PARTIAL_PAYMENT_ORDER
}
