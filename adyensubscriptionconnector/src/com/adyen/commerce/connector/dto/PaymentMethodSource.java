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
 * Where the payment method a shopper picks comes from.
 *
 * <p>Two genuinely different operations. Importing a card the shopper has vaulted with Adyen puts a NEW
 * instrument on the platform, which each platform gates differently - Recurly wants a network transaction id
 * that a token vaulted earlier cannot supply. Repointing at something the platform ALREADY holds moves no
 * card anywhere and needs none of that.</p>
 *
 * <p>A connector names the sources it accepts, and one that accepts neither does not support the change at
 * all; the core offers the shopper exactly what the connector named.</p>
 */
public enum PaymentMethodSource
{
	/** A card Adyen holds for this shopper, imported into the platform as part of the change. */
	ADYEN_VAULTED_TOKEN,

	/** A payment method the billing platform already has on the customer's account. */
	ALREADY_ON_PLATFORM
}
