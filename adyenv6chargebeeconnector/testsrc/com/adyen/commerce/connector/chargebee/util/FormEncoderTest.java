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
package com.adyen.commerce.connector.chargebee.util;

import static org.junit.Assert.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import de.hybris.bootstrap.annotations.UnitTest;

/**
 * Unit test for {@link FormEncoder}: URL-encoding, bracketed keys, null-skipping, order preservation.
 */
@UnitTest
public class FormEncoderTest
{
	@Test
	public void encodesPreservesOrderAndSkipsNulls()
	{
		final Map<String, String> params = new LinkedHashMap<>();
		params.put("a", "1");
		params.put("b", "x y");
		params.put("k[0]", "v/w");
		params.put("skip", null);

		assertEquals("a=1&b=x+y&k%5B0%5D=v%2Fw", FormEncoder.encode(params));
	}

	@Test
	public void encodesEmptyMapToEmptyString()
	{
		assertEquals("", FormEncoder.encode(new LinkedHashMap<>()));
	}
}
