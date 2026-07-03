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
package com.adyen.commerce.connector.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Small shared validation / immutability helpers for the vendor-neutral domain model. Used from the
 * records' compact constructors to enforce invariants and defensively copy mutable inputs. These are
 * programming-contract checks (they throw {@link IllegalArgumentException}); platform/runtime failures
 * are reported via {@link com.adyen.commerce.connector.exception.BillingException} instead.
 */
final class Dtos
{
	private Dtos()
	{
		// utility class
	}

	static String requireText(final String value, final String field)
	{
		if (value == null || value.isBlank())
		{
			throw new IllegalArgumentException(field + " must not be blank");
		}
		return value;
	}

	static <T> T requireValue(final T value, final String field)
	{
		if (value == null)
		{
			throw new IllegalArgumentException(field + " must not be null");
		}
		return value;
	}

	static int requirePositive(final int value, final String field)
	{
		if (value <= 0)
		{
			throw new IllegalArgumentException(field + " must be greater than 0, was " + value);
		}
		return value;
	}

	static <K, V> Map<K, V> immutableCopy(final Map<K, V> map)
	{
		return map == null ? Collections.<K, V> emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(map));
	}
}
