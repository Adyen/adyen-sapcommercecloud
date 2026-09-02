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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Builds {@code application/x-www-form-urlencoded} bodies. Chargebee uses bracketed keys for nested
 * params (e.g. {@code subscription_items[item_price_id][0]}); those are passed through as literal keys.
 * Null values are skipped. Iteration order follows the supplied map (use a LinkedHashMap for determinism).
 */
public final class FormEncoder
{
	private FormEncoder()
	{
		//utility
	}

	public static String encode(final Map<String, String> params)
	{
		final StringBuilder sb = new StringBuilder();
		for (final Map.Entry<String, String> entry : params.entrySet())
		{
			if (entry.getValue() == null)
			{
				continue;
			}
			if (sb.length() > 0)
			{
				sb.append('&');
			}
			sb.append(enc(entry.getKey())).append('=').append(enc(entry.getValue()));
		}
		return sb.toString();
	}

	private static String enc(final String value)
	{
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}
}
