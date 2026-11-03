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

import org.apache.commons.lang3.StringUtils;

/**
 * Where the shopper goes to give the platform a payment method, as a destination rather than an artifact.
 *
 * <p>A URL is data, which is what keeps this inside the SPI's boundary: an adapter that needed a script or a
 * markup fragment in the storefront could not answer here, and would be asking the storefront extension to
 * depend on it.</p>
 *
 * <p>Absolute {@code https} only. The shopper is about to be redirected to whatever this says, and on some
 * platforms the address itself carries the credential that opens the account.</p>
 */
public record PaymentMethodEnrollmentPage(String url)
{
	public PaymentMethodEnrollmentPage
	{
		Dtos.requireText(url, "url");
		if (!StringUtils.startsWith(url, "https://"))
		{
			throw new IllegalArgumentException("A payment-method enrollment page must be an absolute https "
					+ "URL; the shopper is redirected to it unmodified");
		}
	}
}
