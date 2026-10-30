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
package com.adyen.commerce.services.impl;

import com.adyen.commerce.services.BillingPlatformAvailabilityService;

import java.util.Collections;
import java.util.Set;

/**
 * Used when no subscription connector extension is installed: no billing platform is available. The
 * subscription connector extension overrides the {@code billingPlatformAvailabilityService} alias.
 */
public class DefaultBillingPlatformAvailabilityService implements BillingPlatformAvailabilityService {

    @Override
    public Set<String> getAvailablePlatformCodes() {
        return Collections.emptySet();
    }

    @Override
    public boolean isAvailable(final String platformCode) {
        return false;
    }
}
