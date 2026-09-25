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
 * What giving the platform a payment method on its own page does to what it already holds.
 *
 * <p>The shopper is told this before they leave the storefront, because the two are not equally safe to
 * click: one adds an instrument, the other overwrites the one on file.</p>
 */
public enum PaymentMethodEnrollmentEffect
{
	/** The new method joins what the platform holds, and nothing already there changes. */
	ADDS_METHOD,

	/**
	 * The new method takes the place of the one the platform holds for this customer. What Recurly's hosted
	 * account management does: it shows and edits the primary billing info only, so a shopper cannot use it
	 * to put a second card in the wallet.
	 */
	REPLACES_METHOD_ON_FILE
}
