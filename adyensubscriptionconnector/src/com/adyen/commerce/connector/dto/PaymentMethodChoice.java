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
 * What the shopper picked, in the terms the platform will be asked to act on.
 *
 * <p>Sealed, and consumed with a switch <em>expression</em>: only the expression form is checked for
 * exhaustiveness, so a third kind of choice becomes a compile error in every adapter rather than a silent
 * fall-through.</p>
 */
public sealed interface PaymentMethodChoice
{
	PaymentMethodSource source();

	/** A card Adyen holds for this shopper, to be imported into the platform as part of the change. */
	record AdyenVaultedToken(AdyenTokenHandle token) implements PaymentMethodChoice
	{
		public AdyenVaultedToken
		{
			Dtos.requireValue(token, "token");
		}

		@Override
		public PaymentMethodSource source()
		{
			return PaymentMethodSource.ADYEN_VAULTED_TOKEN;
		}
	}

	/** Something the platform already holds, named by its own identifier. */
	record AlreadyOnPlatform(String platformPaymentMethodId) implements PaymentMethodChoice
	{
		public AlreadyOnPlatform
		{
			Dtos.requireText(platformPaymentMethodId, "platformPaymentMethodId");
		}

		@Override
		public PaymentMethodSource source()
		{
			return PaymentMethodSource.ALREADY_ON_PLATFORM;
		}
	}
}
