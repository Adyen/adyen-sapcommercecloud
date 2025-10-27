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
import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.product.data.PriceData;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.math.BigDecimal;

import static org.junit.Assert.*;

@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class RemainingAmountCalculatorTest {

    @Test
    public void testCalculateRemainingAmount_BasicCalculation() {
        // Given
        BigDecimal totalAmount = new BigDecimal("100.00");
        BigDecimal giftCardAmount = new BigDecimal("30.00");
        BigDecimal expectedRemaining = new BigDecimal("70.00");

        // When
        BigDecimal result = RemainingAmountCalculator.calculateRemainingAmount(totalAmount, giftCardAmount);

        // Then
        assertEquals(expectedRemaining, result);
    }

    @Test
    public void testCalculateRemainingAmount_FullCoverage() {
        // Given
        BigDecimal totalAmount = new BigDecimal("50.00");
        BigDecimal giftCardAmount = new BigDecimal("50.00");
        BigDecimal expectedRemaining = new BigDecimal("0.00");

        // When
        BigDecimal result = RemainingAmountCalculator.calculateRemainingAmount(totalAmount, giftCardAmount);

        // Then
        assertEquals(expectedRemaining, result);
    }

    @Test
    public void testCalculateRemainingAmount_WithCartData() {
        // Given
        CartData cartData = new CartData();
        PriceData totalPrice = new PriceData();
        totalPrice.setValue(new BigDecimal("150.00"));
        cartData.setTotalPrice(totalPrice);

        AdyenPartialPaymentOrderData partialPaymentData = new AdyenPartialPaymentOrderData();
        partialPaymentData.setGiftCardChargedAmount(new BigDecimal("25.00"));

        BigDecimal expectedRemaining = new BigDecimal("125.00");

        // When
        BigDecimal result = RemainingAmountCalculator.calculateRemainingAmount(cartData, partialPaymentData);

        // Then
        assertEquals(expectedRemaining, result);
    }

    @Test
    public void testIsFullyCoveredByGiftCard_NotFullyCovered() {
        // Given
        BigDecimal totalAmount = new BigDecimal("100.00");
        BigDecimal giftCardAmount = new BigDecimal("75.00");

        // When
        boolean result = RemainingAmountCalculator.isFullyCoveredByGiftCard(totalAmount, giftCardAmount);

        // Then
        assertFalse(result);
    }

    @Test
    public void testIsFullyCoveredByGiftCard_FullyCovered() {
        // Given
        BigDecimal totalAmount = new BigDecimal("100.00");
        BigDecimal giftCardAmount = new BigDecimal("100.00");

        // When
        boolean result = RemainingAmountCalculator.isFullyCoveredByGiftCard(totalAmount, giftCardAmount);

        // Then
        assertTrue(result);
    }

    @Test
    public void testIsFullyCoveredByGiftCard_OverCovered() {
        // Given
        BigDecimal totalAmount = new BigDecimal("100.00");
        BigDecimal giftCardAmount = new BigDecimal("120.00");

        // When
        boolean result = RemainingAmountCalculator.isFullyCoveredByGiftCard(totalAmount, giftCardAmount);

        // Then
        assertTrue(result);
    }

    @Test
    public void testCalculateCoveragePercentage() {
        // Given
        BigDecimal totalAmount = new BigDecimal("100.00");
        BigDecimal giftCardAmount = new BigDecimal("25.00");
        BigDecimal expectedPercentage = new BigDecimal("0.2500");

        // When
        BigDecimal result = RemainingAmountCalculator.calculateCoveragePercentage(totalAmount, giftCardAmount);

        // Then
        assertEquals(expectedPercentage, result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCalculateRemainingAmount_NullTotalAmount() {
        // Given
        BigDecimal giftCardAmount = new BigDecimal("30.00");

        // When
        RemainingAmountCalculator.calculateRemainingAmount(null, giftCardAmount);

        // Then - exception expected
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCalculateRemainingAmount_NullGiftCardAmount() {
        // Given
        BigDecimal totalAmount = new BigDecimal("100.00");

        // When
        RemainingAmountCalculator.calculateRemainingAmount(totalAmount, null);

        // Then - exception expected
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCalculateRemainingAmount_NegativeTotalAmount() {
        // Given
        BigDecimal totalAmount = new BigDecimal("-100.00");
        BigDecimal giftCardAmount = new BigDecimal("30.00");

        // When
        RemainingAmountCalculator.calculateRemainingAmount(totalAmount, giftCardAmount);

        // Then - exception expected
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCalculateRemainingAmount_NegativeGiftCardAmount() {
        // Given
        BigDecimal totalAmount = new BigDecimal("100.00");
        BigDecimal giftCardAmount = new BigDecimal("-30.00");

        // When
        RemainingAmountCalculator.calculateRemainingAmount(totalAmount, giftCardAmount);

        // Then - exception expected
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCalculateRemainingAmount_GiftCardExceedsTotal() {
        // Given
        BigDecimal totalAmount = new BigDecimal("50.00");
        BigDecimal giftCardAmount = new BigDecimal("75.00");

        // When
        RemainingAmountCalculator.calculateRemainingAmount(totalAmount, giftCardAmount);

        // Then - exception expected
    }
}