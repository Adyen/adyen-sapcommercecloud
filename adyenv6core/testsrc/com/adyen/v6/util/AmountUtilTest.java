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

import com.adyen.model.checkout.Amount;
import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class AmountUtilTest {

    // createAmount — HALF_EVEN (banker's rounding)

    @Test
    public void createAmount_roundsHalfToEven_whenFractionIsExactlyHalf() {
        // 1.225 with scale 2: digit before 5 is 2 (even) → rounds DOWN to 1.22
        Amount amount = AmountUtil.createAmount(new BigDecimal("1.225"), "EUR");
        assertEquals(Long.valueOf(122L), amount.getValue());
    }

    @Test
    public void createAmount_roundsHalfToEven_whenPrecedingDigitIsOdd() {
        // 1.235 with scale 2: digit before 5 is 3 (odd) → rounds UP to 1.24
        Amount amount = AmountUtil.createAmount(new BigDecimal("1.235"), "EUR");
        assertEquals(Long.valueOf(124L), amount.getValue());
    }

    @Test
    public void createAmount_roundsHalfUp_doesNotApply() {
        // Under HALF_UP both 1.225 and 1.235 would round UP (122 → 123; 123 → 124).
        // This test documents that 1.225 becomes 122, NOT 123 under HALF_EVEN.
        Amount amount = AmountUtil.createAmount(new BigDecimal("1.225"), "EUR");
        assertEquals("HALF_EVEN: 1.225 must round to 122 cents, not 123", Long.valueOf(122L), amount.getValue());
    }

    @Test
    public void createAmount_exactAmount_noRounding() {
        Amount amount = AmountUtil.createAmount(new BigDecimal("19.99"), "EUR");
        assertEquals(Long.valueOf(1999L), amount.getValue());
    }

    @Test
    public void createAmount_zeroCurrencyScale_jpy() {
        // JPY has 0 decimal places
        Amount amount = AmountUtil.createAmount(new BigDecimal("1500"), "JPY");
        assertEquals("JPY amount should be in whole units", Long.valueOf(1500L), amount.getValue());
        assertEquals("JPY", amount.getCurrency());
    }

    @Test
    public void createAmount_setsCurrency() {
        Amount amount = AmountUtil.createAmount(new BigDecimal("10.00"), "GBP");
        assertEquals("GBP", amount.getCurrency());
    }

    // createAmount — guard-rail: negative values must be rejected

    @Test(expected = IllegalArgumentException.class)
    public void createAmount_throwsOnNegativeValue() {
        AmountUtil.createAmount(new BigDecimal("-1.00"), "EUR");
    }

    @Test(expected = IllegalArgumentException.class)
    public void createAmount_throwsOnNegativeSmallFraction() {
        AmountUtil.createAmount(new BigDecimal("-0.01"), "EUR");
    }

    @Test
    public void createAmount_acceptsZero() {
        Amount amount = AmountUtil.createAmount(BigDecimal.ZERO, "EUR");
        assertEquals(Long.valueOf(0L), amount.getValue());
    }

    // createAmount — null / blank guards

    @Test(expected = IllegalArgumentException.class)
    public void createAmount_throwsOnNullValue() {
        AmountUtil.createAmount(null, "EUR");
    }

    @Test(expected = IllegalArgumentException.class)
    public void createAmount_throwsOnNullCurrency() {
        AmountUtil.createAmount(BigDecimal.ONE, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void createAmount_throwsOnBlankCurrency() {
        AmountUtil.createAmount(BigDecimal.ONE, "  ");
    }

    // convertFromMinorUnits

    @Test
    public void convertFromMinorUnits_returnsNullForNullInput() {
        assertNull(AmountUtil.convertFromMinorUnits(null, "EUR"));
    }

    @Test
    public void convertFromMinorUnits_convertsCorrectly() {
        BigDecimal result = AmountUtil.convertFromMinorUnits(1999L, "EUR");
        assertEquals(new BigDecimal("19.99"), result);
    }

    @Test
    public void convertFromMinorUnits_handlesZero() {
        BigDecimal result = AmountUtil.convertFromMinorUnits(0L, "EUR");
        assertEquals(BigDecimal.ZERO.setScale(2), result);
    }

    @Test
    public void convertFromMinorUnits_smallValue() {
        BigDecimal result = AmountUtil.convertFromMinorUnits(5L, "EUR");
        assertEquals(new BigDecimal("0.05"), result);
    }
}
