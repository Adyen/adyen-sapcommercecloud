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

import de.hybris.bootstrap.annotations.UnitTest;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Without a subscription connector extension no billing platform may be offered.
 */
@UnitTest
public class DefaultBillingPlatformAvailabilityServiceTest {

    private DefaultBillingPlatformAvailabilityService testObj;

    @Before
    public void setUp() {
        testObj = new DefaultBillingPlatformAvailabilityService();
    }

    @Test
    public void noPlatformCodesAreAvailable() {
        assertTrue(testObj.getAvailablePlatformCodes().isEmpty());
    }

    @Test
    public void noPlatformIsAvailable() {
        assertFalse(testObj.isAvailable("RECURLY"));
        assertFalse(testObj.isAvailable("CHARGEBEE"));
    }

    @Test
    public void nullCodeIsNotAvailable() {
        assertFalse(testObj.isAvailable(null));
    }
}
