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
package com.adyen.commerce.connector.spi;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import org.junit.Test;

import com.adyen.commerce.connector.dto.BillingSubscriptionRef;
import com.adyen.commerce.connector.dto.SubscriptionPauseRequest;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.CapabilityUnsupportedException;

import de.hybris.bootstrap.annotations.UnitTest;

/**
 * Contract test for the {@link SubscriptionBillingConnector} SPI: a connector that does not override
 * {@code pauseSubscription} rejects pause with {@link CapabilityUnsupportedException}.
 */
@UnitTest
public class SubscriptionBillingConnectorTest
{
	@Test
	public void defaultPauseRejectsWithCapabilityUnsupported()
	{
		final SubscriptionBillingConnector connector = mock(SubscriptionBillingConnector.class, CALLS_REAL_METHODS);
		doReturn(BillingPlatform.CHARGEBEE).when(connector).platform();

		final SubscriptionPauseRequest request = new SubscriptionPauseRequest(
				new BillingSubscriptionRef(BillingPlatform.CHARGEBEE, "sub-1"), null, "key-1");

		assertThrows(CapabilityUnsupportedException.class, () -> connector.pauseSubscription(request));
	}
}
