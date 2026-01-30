package com.adyen.commerce.utils;

import de.hybris.platform.subscriptionservices.enums.BillingCycleType;
import de.hybris.platform.subscriptionservices.model.BillingPlanModel;
import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

public class NextChargeDateUtil {
    private static final Logger LOG = LoggerFactory.getLogger(NextChargeDateUtil.class);

    public static LocalDate calculateNextChargeDate(final Date orderDate, final BillingPlanModel billingPlanModel) {
        return calculateNextChargeDate(orderDate, billingPlanModel.getBillingCycleType(), billingPlanModel.getBillingFrequency().getCode(), billingPlanModel.getBillingCycleDay());
    }

    public static LocalDate calculateNextChargeDate(final Date orderDate, final BillingCycleType billingCycleType, final String billingFrequency, final Integer billingCycleDay) {
        LocalDate localOrderDate = LocalDate.fromDateFields(orderDate);

        switch (billingCycleType) {
            case SUBSCRIPTION_START:
                return calculateNextChargeSubscriptionStart(localOrderDate, billingFrequency);
            case DAY_OF_MONTH:
                return calculateNextChargeDayOfMonth(localOrderDate, billingFrequency, billingCycleDay);
            case END_OF_MONTH:
                return calculateNextChargeEndOfMonth(localOrderDate, billingFrequency);
            default:
                String errorMessage = "Unsupported billing cycle type: " + billingCycleType;
                LOG.error(errorMessage);
                throw new IllegalArgumentException(errorMessage);
        }
    }

    protected static LocalDate calculateNextChargeSubscriptionStart(final LocalDate orderDate, final String billingFrequency) {

        switch (billingFrequency.toLowerCase()) {
            case "monthly":
                return orderDate.plusMonths(1);
            case "quarterly":
                return orderDate.plusMonths(3);
            case "yearly":
                return orderDate.plusMonths(12);
            default:
                String errorMessage = "Unsupported billing frequency code: " + billingFrequency;
                LOG.error(errorMessage);
                throw new IllegalArgumentException(errorMessage);
        }
    }

    protected static LocalDate calculateNextChargeDayOfMonth(final LocalDate orderDate, final String billingFrequency, final Integer billingCycleDay) {

        if (billingCycleDay == null) {
            String errorMessage = "No billingCycleDay for charge on specific day of the month";
            LOG.error(errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }

        int maxDayOfMonth;

        switch (billingFrequency.toLowerCase()) {
            case "monthly":
                maxDayOfMonth = getBaseLocalDateForEndOfMonth(orderDate).plusMonths(1).dayOfMonth().withMaximumValue().getDayOfMonth();
                if (billingCycleDay > maxDayOfMonth) {
                    LOG.warn("Billing cycle day greater than last day of the month, using last day of the month");
                }
                return getBaseLocalDateForEndOfMonth(orderDate).plusMonths(1).withDayOfMonth(Math.min(maxDayOfMonth, billingCycleDay));
            case "quarterly":
                maxDayOfMonth = getBaseLocalDateForEndOfMonth(orderDate).plusMonths(3).dayOfMonth().withMaximumValue().getDayOfMonth();
                if (billingCycleDay > maxDayOfMonth) {
                    LOG.warn("Billing cycle day greater than last day of the month, using last day of the month");
                }
                return getBaseLocalDateForEndOfMonth(orderDate).plusMonths(3).withDayOfMonth(Math.min(maxDayOfMonth, billingCycleDay));
            case "yearly":
                maxDayOfMonth = getBaseLocalDateForEndOfMonth(orderDate).plusMonths(12).dayOfMonth().withMaximumValue().getDayOfMonth();
                if (billingCycleDay > maxDayOfMonth) {
                    LOG.warn("Billing cycle day greater than last day of the month, using last day of the month");
                }
                return getBaseLocalDateForEndOfMonth(orderDate).plusMonths(12).withDayOfMonth(Math.min(maxDayOfMonth, billingCycleDay));
            default:
                String errorMessage = "Unsupported billing frequency code: " + billingFrequency;
                LOG.error(errorMessage);
                throw new IllegalArgumentException(errorMessage);
        }
    }

    protected static LocalDate calculateNextChargeEndOfMonth(final LocalDate orderDate, final String billingFrequency) {

        switch (billingFrequency.toLowerCase()) {
            case "monthly":
                return getBaseLocalDateForEndOfMonth(orderDate).plusMonths(1).dayOfMonth().withMaximumValue();
            case "quarterly":
                return getBaseLocalDateForEndOfMonth(orderDate).plusMonths(3).dayOfMonth().withMaximumValue();
            case "yearly":
                return getBaseLocalDateForEndOfMonth(orderDate).plusMonths(12).dayOfMonth().withMaximumValue();
            default:
                String errorMessage = "Unsupported billing frequency code: " + billingFrequency;
                LOG.error(errorMessage);
                throw new IllegalArgumentException(errorMessage);
        }
    }


    /**
     *  Used to prevent skipping month if next month has fewer days
     */
    protected static LocalDate getBaseLocalDateForEndOfMonth(final LocalDate orderDate){
        if(orderDate.getDayOfMonth() > 27){
            return orderDate.minusDays(5);
        }
        return orderDate;
    }

}
