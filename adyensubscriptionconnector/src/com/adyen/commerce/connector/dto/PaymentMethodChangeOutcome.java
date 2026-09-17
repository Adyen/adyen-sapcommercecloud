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

/**
 * What a completed payment-method change actually did.
 *
 * <p>{@code appliedScope} reports what the connector did rather than what it declared, so the caller can
 * tell the shopper which subscriptions moved and a test can assert the two agree. {@code NOT_SUPPORTED} is
 * not a legal value here: a connector that cannot do it raises
 * {@link com.adyen.commerce.connector.exception.CapabilityUnsupportedException} instead of returning a
 * success that did nothing.</p>
 */
public record PaymentMethodChangeOutcome(BillingPaymentMethodRef paymentMethod,
                                         PaymentMethodChangeScope appliedScope)
{
	public PaymentMethodChangeOutcome
	{
		Dtos.requireValue(paymentMethod, "paymentMethod");
		Dtos.requireValue(appliedScope, "appliedScope");
		if (!appliedScope.isSupported())
		{
			throw new IllegalArgumentException("A completed payment-method change cannot report a scope of "
					+ "NOT_SUPPORTED; a connector that cannot perform it must refuse rather than succeed");
		}
	}
}
