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
 * Whether a connector can send the shopper somewhere to give the platform a payment method, and what
 * happens when they do.
 *
 * <p>Declared rather than discovered, so the page can decide whether to show the invitation without an API
 * call per render — and, on platforms whose link doubles as a credential, without minting one that nobody
 * clicked.</p>
 */
public record PaymentMethodEnrollmentSupport(PaymentMethodEnrollmentEffect effect)
{
	/** Declared by a platform that hosts no such page, and by adapters that have not implemented one. */
	public static final PaymentMethodEnrollmentSupport NONE = new PaymentMethodEnrollmentSupport(null);

	public boolean isOffered()
	{
		return effect != null;
	}
}
