package com.adyen.commerce.services.impl;

import com.adyen.model.recurring.Recurring;
import com.adyen.v6.enums.RecurringContractMode;

/**
 * Helper class for recurring contract operations
 */
public class RecurringContractHelper {

    /**
     * Returns Recurring object from RecurringContractMode
     */
    public static Recurring getRecurringContractType(RecurringContractMode recurringContractMode) {
        if (recurringContractMode == null || RecurringContractMode.NONE.equals(recurringContractMode)) {
            return null;
        }

        String recurringMode = recurringContractMode.getCode();
        Recurring.ContractEnum contractEnum = Recurring.ContractEnum.valueOf(recurringMode);
        
        return new Recurring().contract(contractEnum);
    }

    /**
     * Returns the recurringContract. If the user did not want to save the card don't send it as ONECLICK
     */
    public static Recurring getRecurringContractType(RecurringContractMode recurringContractMode, Boolean enableOneClick) {
        Recurring recurringContract = getRecurringContractType(recurringContractMode);

        if (recurringContract == null) {
            return null;
        }

        // if user wants to save their card use the configured recurring contract type
        if (Boolean.TRUE.equals(enableOneClick)) {
            return recurringContract;
        }

        Recurring.ContractEnum contractEnum = recurringContract.getContract();
        /*
         * If save card is not checked do the following changes:
         * NONE => NONE
         * ONECLICK => NONE
         * ONECLICK,RECURRING => RECURRING
         * RECURRING => RECURRING
         */
        if (Recurring.ContractEnum.RECURRING.equals(contractEnum)) {
            return recurringContract.contract(Recurring.ContractEnum.RECURRING);
        }

        return null;
    }
}