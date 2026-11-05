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
 * A payment method the shopper can choose.
 */
public class AdyenSubscriptionPaymentMethodOptionWsDTO implements Serializable
{
	private static final long serialVersionUID = 1L;

	private String id;

	private String displayLabel;

	private boolean current;

	public String getId()
	{
		return id;
	}

	public void setId(final String id)
	{
		this.id = id;
	}

	public String getDisplayLabel()
	{
		return displayLabel;
	}

	public void setDisplayLabel(final String displayLabel)
	{
		this.displayLabel = displayLabel;
	}

	public boolean isCurrent()
	{
		return current;
	}

	public void setCurrent(final boolean current)
	{
		this.current = current;
	}
}
