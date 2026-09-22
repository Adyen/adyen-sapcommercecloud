package com.adyen.commerce.utils;

import de.hybris.platform.subscriptionservices.enums.BillingCycleType;
import de.hybris.platform.subscriptionservices.model.BillingFrequencyModel;
import de.hybris.platform.subscriptionservices.model.BillingPlanModel;
import org.joda.time.LocalDate;
import org.junit.Before;
import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NextChargeDateUtilTest {
    private BillingPlanModel mockBillingPlan;
    private BillingFrequencyModel mockBillingFrequency;

    @Before
    public void setUp() {
        mockBillingPlan = mock(BillingPlanModel.class);
        mockBillingFrequency = mock(BillingFrequencyModel.class);
        when(mockBillingPlan.getBillingFrequency()).thenReturn(mockBillingFrequency);
    }

    // --- Tests for calculateNextChargeDate(Date, BillingPlanModel) (The primary public method) ---

    @Test
    public void testCalculateNextChargeDateWithBillingPlan_SubscriptionStart_Monthly() {
        // Arrange
        LocalDate orderDate = new LocalDate(2023, 10, 15);
        Date orderJavaDate = orderDate.toDate();

        when(mockBillingPlan.getBillingCycleType()).thenReturn(BillingCycleType.SUBSCRIPTION_START);
        when(mockBillingFrequency.getCode()).thenReturn("monthly");

        // Act
        LocalDate nextChargeDate = NextChargeDateUtil.calculateNextChargeDate(orderJavaDate, mockBillingPlan);

        // Assert
        assertEquals(new LocalDate(2023, 11, 15), nextChargeDate);
    }

    // --- Tests for SUBSCRIPTION_START ---

    @Test
    public void testCalculateNextChargeSubscriptionStart_Monthly() {
        LocalDate orderDate = new LocalDate(2024, 1, 1);
        LocalDate expectedDate = new LocalDate(2024, 2, 1);
        LocalDate actualDate = NextChargeDateUtil.calculateNextChargeSubscriptionStart(orderDate, "monthly");
        assertEquals(expectedDate, actualDate);
    }

    @Test
    public void testCalculateNextChargeSubscriptionStart_Quarterly() {
        LocalDate orderDate = new LocalDate(2023, 5, 20);
        LocalDate expectedDate = new LocalDate(2023, 8, 20);
        LocalDate actualDate = NextChargeDateUtil.calculateNextChargeSubscriptionStart(orderDate, "quarterly");
        assertEquals(expectedDate, actualDate);
    }

    @Test
    public void testCalculateNextChargeSubscriptionStart_Yearly() {
        LocalDate orderDate = new LocalDate(2023, 1, 10);
        LocalDate expectedDate = new LocalDate(2024, 1, 10);
        LocalDate actualDate = NextChargeDateUtil.calculateNextChargeSubscriptionStart(orderDate, "yearly");
        assertEquals(expectedDate, actualDate);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCalculateNextChargeSubscriptionStart_UnsupportedFrequency() {
        NextChargeDateUtil.calculateNextChargeSubscriptionStart(new LocalDate(2023, 1, 1), "biweekly");
    }

    // --- Tests for END_OF_MONTH ---

    @Test
    public void testCalculateNextChargeEndOfMonth_Monthly() {
        // Order date 2023-10-15, next EOM should be 2023-11-30
        LocalDate orderDate = new LocalDate(2023, 10, 15);
        LocalDate expectedDate = new LocalDate(2023, 11, 30);
        LocalDate actualDate = NextChargeDateUtil.calculateNextChargeEndOfMonth(orderDate, "monthly");
        assertEquals(expectedDate, actualDate);
    }

    @Test
    public void testCalculateNextChargeEndOfMonth_Monthly_FebInLeapYear() {
        // Order date 2024-01-30 (leap year), next EOM should be 2024-02-29
        LocalDate orderDate = new LocalDate(2024, 1, 30); // Base date will be adjusted to 2024-01-25
        LocalDate expectedDate = new LocalDate(2024, 2, 29);
        LocalDate actualDate = NextChargeDateUtil.calculateNextChargeEndOfMonth(orderDate, "monthly");
        assertEquals(expectedDate, actualDate);
    }

    @Test
    public void testCalculateNextChargeEndOfMonth_Quarterly() {
        // Order date 2023-01-01, next EOM after 3 months should be 2023-04-30
        LocalDate orderDate = new LocalDate(2023, 1, 1);
        LocalDate expectedDate = new LocalDate(2023, 4, 30);
        LocalDate actualDate = NextChargeDateUtil.calculateNextChargeEndOfMonth(orderDate, "quarterly");
        assertEquals(expectedDate, actualDate);
    }

    @Test
    public void testCalculateNextChargeEndOfMonth_Yearly() {
        // Order date 2023-01-01, next EOM after 12 months should be 2024-01-31
        LocalDate orderDate = new LocalDate(2023, 1, 1);
        LocalDate expectedDate = new LocalDate(2024, 1, 31);
        LocalDate actualDate = NextChargeDateUtil.calculateNextChargeEndOfMonth(orderDate, "yearly");
        assertEquals(expectedDate, actualDate);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCalculateNextChargeEndOfMonth_UnsupportedFrequency() {
        NextChargeDateUtil.calculateNextChargeEndOfMonth(new LocalDate(2023, 1, 1), "semiannual");
    }

    // --- Tests for DAY_OF_MONTH (Including requested edge cases) ---

    // ⭐️ Edge Case: Next charge date is the 31st, but next month (Nov) only has 30 days.
    @Test
    public void testCalculateNextChargeDayOfMonth_Monthly_Day31_ShortMonth_RollsTo30() {
        // Order date 2023-10-15 (base date will be 2023-10-15), Next charge on day 31 of next month (November).
        LocalDate orderDate = new LocalDate(2023, 10, 15);
        // Next month (November) only has 30 days. Expected to roll to 30.
        LocalDate expectedDate = new LocalDate(2023, 11, 30);
        LocalDate actualDate = NextChargeDateUtil.calculateNextChargeDayOfMonth(orderDate, "monthly", 31);
        assertEquals(expectedDate, actualDate);
    }

    @Test
    public void testCalculateNextChargeDayOfMonth_Monthly_OrderDateAfter27th_Day31() {
        // Order date 2023-10-29 (base date adjusted to 2023-10-24). Next charge on day 30 of next month (November).
        LocalDate orderDate = new LocalDate(2023, 10, 29);
        LocalDate expectedDate = new LocalDate(2023, 11, 30);
        LocalDate actualDate = NextChargeDateUtil.calculateNextChargeDayOfMonth(orderDate, "monthly", 31);
        assertEquals(expectedDate, actualDate);
    }

    @Test
    public void testCalculateNextChargeDayOfMonth_Monthly_OrderDate30Jan_NextChargeInMarch() {
        // Order date 2024-01-10 (leap year). Base date adjusted to 2024-02-29. Next charge on day 29 of next month (February).
        LocalDate orderDate = new LocalDate(2024, 1, 10);
        LocalDate expectedDate = new LocalDate(2024, 2, 29);
        LocalDate actualDate = NextChargeDateUtil.calculateNextChargeDayOfMonth(orderDate, "monthly", 30);
        assertEquals(expectedDate, actualDate);
    }

    // Standard case for DAY_OF_MONTH
    @Test
    public void testCalculateNextChargeDayOfMonth_Monthly_StandardCase() {
        // Order date 2023-10-15. Next charge on day 10 of next month (November).
        LocalDate orderDate = new LocalDate(2023, 10, 15);
        LocalDate expectedDate = new LocalDate(2023, 11, 10);
        LocalDate actualDate = NextChargeDateUtil.calculateNextChargeDayOfMonth(orderDate, "monthly", 10);
        assertEquals(expectedDate, actualDate);
    }

    // Test for Quarterly frequency
    @Test
    public void testCalculateNextChargeDayOfMonth_Quarterly_StandardCase() {
        // Order date 2023-10-15. Next charge on day 10 of month 3 months later (January 2024).
        LocalDate orderDate = new LocalDate(2023, 10, 15);
        LocalDate expectedDate = new LocalDate(2024, 1, 10);
        LocalDate actualDate = NextChargeDateUtil.calculateNextChargeDayOfMonth(orderDate, "quarterly", 10);
        assertEquals(expectedDate, actualDate);
    }

    // Test for Quarterly frequency with day exceeding short month
    @Test
    public void testCalculateNextChargeDayOfMonth_Quarterly_Rollback() {

        // Order date 2023-01-01. 3 months later is April 2023 (30 days). Charge day 15.
        LocalDate orderDate = new LocalDate(2023, 8, 1);
        LocalDate expectedDate = new LocalDate(2023, 11, 30);
        LocalDate actualDate = NextChargeDateUtil.calculateNextChargeDayOfMonth(orderDate, "quarterly", 31);
        assertEquals(expectedDate, actualDate);
    }


    // Test for Yearly frequency
    @Test
    public void testCalculateNextChargeDayOfMonth_Yearly_StandardCase() {
        // Order date 2023-10-15. Next charge on day 5 of month 12 months later (October 2024).
        LocalDate orderDate = new LocalDate(2023, 10, 15);
        LocalDate expectedDate = new LocalDate(2024, 10, 5);
        LocalDate actualDate = NextChargeDateUtil.calculateNextChargeDayOfMonth(orderDate, "yearly", 5);
        assertEquals(expectedDate, actualDate);
    }

    // Test for Yearly frequency with rollbacl
    @Test
    public void testCalculateNextChargeDayOfMonth_Yearly_Rollback() {
        // Order date 2023-10-15. Next charge on day 31 of month 12 months later (November 2024).
        LocalDate orderDate = new LocalDate(2023, 11, 15);
        LocalDate expectedDate = new LocalDate(2024, 11, 30);
        LocalDate actualDate = NextChargeDateUtil.calculateNextChargeDayOfMonth(orderDate, "yearly", 31);
        assertEquals(expectedDate, actualDate);
    }


    @Test(expected = IllegalArgumentException.class)
    public void testCalculateNextChargeDayOfMonth_MissingBillingCycleDay() {
        NextChargeDateUtil.calculateNextChargeDayOfMonth(new LocalDate(2023, 1, 1), "monthly", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCalculateNextChargeDayOfMonth_ZeroBillingCycleDay() {
        NextChargeDateUtil.calculateNextChargeDayOfMonth(new LocalDate(2023, 1, 1), "monthly", 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCalculateNextChargeDayOfMonth_NegativeBillingCycleDay() {
        NextChargeDateUtil.calculateNextChargeDayOfMonth(new LocalDate(2023, 1, 1), "monthly", -1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCalculateNextChargeDayOfMonth_UnsupportedFrequency() {
        NextChargeDateUtil.calculateNextChargeDayOfMonth(new LocalDate(2023, 1, 1), "weekly", 15);
    }
}
