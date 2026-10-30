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

import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.core.model.order.AbstractOrderModel;

/**
 * Runs the {@link PaymentAuthorisationGuard}s for the cart that is about to be paid.
 */
public interface PaymentAuthorisationGuardService
{
    /**
     * @param cartData the cart being paid for
     * @param stage    where in the checkout the payment is
     * @throws PaymentAuthorisationRefusedException when a guard refuses; the first refusal wins
     */
    void check(CartData cartData, PaymentAuthorisationStage stage);

    /**
     * The same, for a caller that already holds the cart model.
     *
     * @throws PaymentAuthorisationRefusedException when a guard refuses; the first refusal wins
     */
    void check(AbstractOrderModel cart, PaymentAuthorisationStage stage);
}
