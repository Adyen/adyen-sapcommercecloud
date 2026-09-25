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
 * Whose future billing a payment-method change moves, as the connector declares it.
 *
 * <p>One value rather than a pair of booleans, so a connector cannot claim support without saying what the
 * change touches; the core branches on this and never on the platform's identity. A connector declares
 * exactly one scope and applies it consistently — a platform capable of both picks the one it will actually
 * do, because the core must not predict.</p>
 */
public enum PaymentMethodChangeScope
{
	/**
	 * The shopper cannot change it here: the platform's API cannot do it, or the account is not on a plan
	 * that includes it. The page says so rather than offering a control that cannot work.
	 */
	NOT_SUPPORTED,

	/**
	 * The change moves every subscription this customer has on this platform. True of Chargebee as this
	 * integration drives it: the payment source belongs to the customer and the import replaces the
	 * primary one.
	 */
	CUSTOMER,

	/**
	 * The change moves only the subscription it was asked about. What Recurly offers on sites that carry
	 * the Subscriber Wallet feature, where a subscription can be pinned to one billing info.
	 */
	SUBSCRIPTION;

	public boolean isSupported()
	{
		return this != NOT_SUPPORTED;
	}
}
