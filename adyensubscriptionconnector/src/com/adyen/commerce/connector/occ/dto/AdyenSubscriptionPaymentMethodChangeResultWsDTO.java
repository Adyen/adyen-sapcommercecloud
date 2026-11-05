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
package com.adyen.commerce.connector.occ.dto;

import java.io.Serializable;

/**
 * What a payment method change did. {@code result} is the PaymentMethodChangeResult name.
 */
public class AdyenSubscriptionPaymentMethodChangeResultWsDTO implements Serializable
{
	private static final long serialVersionUID = 1L;

	private String result;

	private int moved;

	private int failed;

	private boolean partial;

	public String getResult()
	{
		return result;
	}

	public void setResult(final String result)
	{
		this.result = result;
	}

	public int getMoved()
	{
		return moved;
	}

	public void setMoved(final int moved)
	{
		this.moved = moved;
	}

	public int getFailed()
	{
		return failed;
	}

	public void setFailed(final int failed)
	{
		this.failed = failed;
	}

	public boolean isPartial()
	{
		return partial;
	}

	public void setPartial(final boolean partial)
	{
		this.partial = partial;
	}
}
