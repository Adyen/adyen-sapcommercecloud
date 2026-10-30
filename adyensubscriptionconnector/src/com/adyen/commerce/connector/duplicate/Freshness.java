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
package com.adyen.commerce.connector.duplicate;

/**
 * How much the answer to "does the customer already own this?" may cost.
 */
public enum Freshness
{
	/** Own database only, no call to a billing platform. For the cart, where the answer is a courtesy. */
	LOCAL,

	/**
	 * May call the billing platform: stale records are re-read and the platform is asked what it holds for the
	 * customer. For the check in front of a payment authorisation. A platform that cannot be reached
	 * degrades the answer to {@link #LOCAL}; it never fails it.
	 */
	REFRESH
}
