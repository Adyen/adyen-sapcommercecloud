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
package com.adyen.commerce.connector.facades.data;

import java.io.Serializable;

/**
 * One entry of a payment-method picker.
 *
 * <p>{@code id} travels back verbatim in the form; the facade reads it the way the subscription's connector
 * declares and checks it against what the shopper holds, so nothing here is trusted on the way back.</p>
 */
public class PaymentMethodOptionData implements Serializable
{
	private static final long serialVersionUID = 1L;

	private String id;
	/** Composed by the platform's adapter or from the Adyen vault listing, never by the page. */
	private String displayLabel;
	/** Whether this is what the subscription, or the account for an account-wide picker, is billed to now. */
	private boolean current;

	public PaymentMethodOptionData()
	{
	}

	public PaymentMethodOptionData(final String id, final String displayLabel, final boolean current)
	{
		this.id = id;
		this.displayLabel = displayLabel;
		this.current = current;
	}

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
