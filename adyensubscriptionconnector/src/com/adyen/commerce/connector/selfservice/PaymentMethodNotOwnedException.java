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
package com.adyen.commerce.connector.selfservice;

import com.adyen.commerce.connector.exception.PreconditionFailedException;

/**
 * The payment method a shopper chose is not one they hold: neither among the cards Adyen vaults for them nor
 * among the methods the subscription's platform holds for their account.
 *
 * <p>A decision, not a failure, and never worth retrying. A listing that could not be read says nothing
 * about ownership and is raised as an ordinary failure instead.</p>
 */
public class PaymentMethodNotOwnedException extends PreconditionFailedException
{
	public PaymentMethodNotOwnedException(final String message)
	{
		super(message);
	}
}
