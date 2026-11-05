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
 * Where the check was asked from. Used only to tell the log lines apart.
 */
public enum DuplicateCheckStage
{
	ADD_TO_CART("add_to_cart"),
	UPDATE_QUANTITY("update_quantity"),
	CART_VALIDATION("cart_validation"),
	/**
	 * Reserved, not asked from anywhere: the platform's cart-merge hook gets no list of modifications to write
	 * into, so a cart merged on login is weighed at the next validation instead.
	 */
	MERGE("merge"),
	PRE_AUTH("pre_auth");

	private final String logValue;

	DuplicateCheckStage(final String logValue)
	{
		this.logValue = logValue;
	}

	/** The value of the {@code stage} log field. */
	public String logValue()
	{
		return logValue;
	}
}
