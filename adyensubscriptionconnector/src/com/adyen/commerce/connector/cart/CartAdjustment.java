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
package com.adyen.commerce.connector.cart;

import com.adyen.commerce.connector.duplicate.DuplicateCheckStage;

import de.hybris.platform.core.model.order.AbstractOrderEntryModel;

/**
 * What has to happen to one cart entry so the cart holds at most one subscription, once, that the customer does not
 * already own.
 *
 * @param entry        the entry to change
 * @param statusCode   one of {@link SubscriptionCartStatus}
 * @param keepQuantity the quantity the entry keeps; {@code 0} means the entry goes
 * @param stage        where the check ran, for the log
 */
public record CartAdjustment(AbstractOrderEntryModel entry, String statusCode, long keepQuantity,
		DuplicateCheckStage stage)
{
	public boolean removesEntry()
	{
		return keepQuantity <= 0;
	}
}
