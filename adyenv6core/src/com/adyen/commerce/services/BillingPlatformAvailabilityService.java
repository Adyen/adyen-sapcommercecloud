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
package com.adyen.commerce.services;

import java.util.Set;

/**
 * Tells which subscription billing platforms have a connector extension loaded.
 * <p>
 * Codes are {@code BillingPlatform} enum codes. The enum itself is declared by the subscription connector
 * extension, so this port speaks plain codes and stays usable where that extension is not installed; with no
 * subscription connector installed no platform is available.
 */
public interface BillingPlatformAvailabilityService {

    /**
     * @return codes of the billing platforms whose connector is loaded; empty when no subscription connector
     * is installed
     */
    Set<String> getAvailablePlatformCodes();

    /**
     * @param platformCode a {@code BillingPlatform} code
     * @return whether a connector for that platform is loaded
     */
    boolean isAvailable(String platformCode);
}
