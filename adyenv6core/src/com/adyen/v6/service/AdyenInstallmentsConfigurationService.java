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
package com.adyen.v6.service;

import com.adyen.v6.dto.InstallmentOptionsDTO;

/**
 * Service for handling installment configuration for different countries
 */
public interface AdyenInstallmentsConfigurationService {

    /**
     * Get installment options for the current country from cart delivery address.
     * Returns null if installments are not supported for the country.
     *
     * According to Adyen documentation, installments are supported in:
     * - Brazil: Any number equal to or greater than 1, and less than 100
     * - Mexico: 3, 6, 9, 12, or 18 monthly installments
     * - Japan: Regular (>1, <100), Revolving, and Bonus installment plans
     *
     * @return InstallmentOptionsDTO for the current country or null if not supported
     */
    InstallmentOptionsDTO getInstallmentOptionsForCountry();
}