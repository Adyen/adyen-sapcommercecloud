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
 *  Copyright (c) 2025 Adyen B.V.
 *  This file is open source and available under the MIT license.
 *  See the LICENSE file for more info.
 */
package com.adyen.v6.util;

import com.adyen.commerce.data.AdyenPartialPaymentOrderData;
import de.hybris.platform.commercefacades.order.data.CartData;
import org.apache.commons.lang3.Validate;
import org.apache.log4j.Logger;

import java.math.BigDecimal;

/**
 * Utility class for calculating remaining amounts in partial payment scenarios.
 * This helper centralizes the logic for determining how much remains to be paid
 * after gift card or other partial payments have been applied.
 */
public final class RemainingAmountCalculator {

    private static final Logger LOGGER = Logger.getLogger(RemainingAmountCalculator.class);

    private RemainingAmountCalculator() {
        // Utility class - prevent instantiation
    }

    /**
     * Calculates the remaining amount to be paid after applying a gift card payment.
     * 
     * @param totalAmount the total cart amount
     * @param giftCardAmount the amount charged to the gift card
     * @return the remaining amount to be paid
     * @throws IllegalArgumentException if any parameter is null or if giftCardAmount exceeds totalAmount
     */
    public static BigDecimal calculateRemainingAmount(BigDecimal totalAmount, BigDecimal giftCardAmount) {
        Validate.notNull(totalAmount, "Total amount cannot be null");
        Validate.notNull(giftCardAmount, "Gift card amount cannot be null");
        Validate.isTrue(totalAmount.compareTo(BigDecimal.ZERO) >= 0, "Total amount cannot be negative");
        Validate.isTrue(giftCardAmount.compareTo(BigDecimal.ZERO) >= 0, "Gift card amount cannot be negative");
        Validate.isTrue(giftCardAmount.compareTo(totalAmount) <= 0, 
            "Gift card amount cannot exceed total amount. Gift card: %s, Total: %s", giftCardAmount, totalAmount);

        BigDecimal remainingAmount = totalAmount.subtract(giftCardAmount);
        
        LOGGER.debug(String.format("Calculated remaining amount: Total=%s, GiftCard=%s, Remaining=%s", 
            totalAmount, giftCardAmount, remainingAmount));
        
        return remainingAmount;
    }

    /**
     * Calculates the remaining amount using cart data and partial payment data.
     * 
     * @param cartData the cart data containing total price information
     * @param partialPaymentData the partial payment data containing gift card charged amount
     * @return the remaining amount to be paid
     * @throws IllegalArgumentException if any parameter is null or if required data is missing
     */
    public static BigDecimal calculateRemainingAmount(CartData cartData, AdyenPartialPaymentOrderData partialPaymentData) {
        Validate.notNull(cartData, "Cart data cannot be null");
        Validate.notNull(partialPaymentData, "Partial payment data cannot be null");
        Validate.notNull(cartData.getTotalPrice(), "Cart total price cannot be null");
        Validate.notNull(cartData.getTotalPrice().getValue(), "Cart total price value cannot be null");
        Validate.notNull(partialPaymentData.getGiftCardChargedAmount(), "Gift card charged amount cannot be null");

        BigDecimal totalAmount = cartData.getTotalPrice().getValue();
        BigDecimal giftCardAmount = partialPaymentData.getGiftCardChargedAmount();

        return calculateRemainingAmount(totalAmount, giftCardAmount);
    }

    /**
     * Checks if the remaining amount is zero or negative, indicating full payment coverage.
     * 
     * @param totalAmount the total cart amount
     * @param giftCardAmount the amount charged to the gift card
     * @return true if the gift card covers the full amount, false otherwise
     */
    public static boolean isFullyCoveredByGiftCard(BigDecimal totalAmount, BigDecimal giftCardAmount) {
        BigDecimal remainingAmount = calculateRemainingAmount(totalAmount, giftCardAmount);
        return remainingAmount.compareTo(BigDecimal.ZERO) <= 0;
    }

    /**
     * Checks if the remaining amount is zero or negative using cart and partial payment data.
     * 
     * @param cartData the cart data containing total price information
     * @param partialPaymentData the partial payment data containing gift card charged amount
     * @return true if the gift card covers the full amount, false otherwise
     */
    public static boolean isFullyCoveredByGiftCard(CartData cartData, AdyenPartialPaymentOrderData partialPaymentData) {
        BigDecimal remainingAmount = calculateRemainingAmount(cartData, partialPaymentData);
        return remainingAmount.compareTo(BigDecimal.ZERO) <= 0;
    }
}