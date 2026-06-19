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

import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.order.InvalidCartException;
import de.hybris.platform.order.exceptions.CalculationException;

/**
 * Service responsible for locking, restoring, and rebuilding the session cart
 * during and after Adyen payment flows.
 */
public interface AdyenCartRestorationService {

    /**
     * Locks the current session cart so it cannot be modified during payment.
     * Stores it in the session under a dedicated key and removes it from the
     * active cart session attribute.
     */
    void lockSessionCart();

    /**
     * Restores the previously locked session cart.
     *
     * @return the restored {@link CartModel}
     * @throws InvalidCartException if no locked cart is found in the session
     */
    CartModel restoreSessionCart() throws InvalidCartException;

    /**
     * Restores the session cart from a placed order identified by its code.
     * Used in the Accelerator (non-OCC) flow.
     *
     * @param orderCode the order code to restore from
     * @throws CalculationException if cart recalculation fails
     * @throws InvalidCartException if the order cannot be found or the cart cannot be restored
     */
    void restoreCartFromOrder(String orderCode) throws CalculationException, InvalidCartException;

    /**
     * Restores the session cart from a placed order identified by its code.
     * Used in the OCC flow — resolves the order via OCC-specific lookup.
     *
     * @param orderCode the order code to restore from
     * @throws CalculationException if cart recalculation fails
     * @throws InvalidCartException if the order cannot be found or the cart cannot be restored
     */
    void restoreCartFromOrderOCC(String orderCode) throws CalculationException, InvalidCartException;

    /**
     * Reads the pending order code from the session, marks the order as errored,
     * triggers the business process event, and restores the cart.
     * Called when a payment error occurs and the user needs to retry.
     *
     * @throws InvalidCartException if the order cannot be found
     * @throws CalculationException if cart recalculation fails
     */
    void restoreCartFromOrderCodeInSession() throws InvalidCartException, CalculationException;
}
