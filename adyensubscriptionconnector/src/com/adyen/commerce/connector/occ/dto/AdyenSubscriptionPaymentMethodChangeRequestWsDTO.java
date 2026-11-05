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
 * Body of the payment method change. A plain POJO with setters, because OCC binds bodies with MOXy.
 */
public class AdyenSubscriptionPaymentMethodChangeRequestWsDTO implements Serializable
{
	private static final long serialVersionUID = 1L;

	private String platform;

	private String storedPaymentMethodId;

	private boolean applyToAll;

	public String getPlatform()
	{
		return platform;
	}

	public void setPlatform(final String platform)
	{
		this.platform = platform;
	}

	public String getStoredPaymentMethodId()
	{
		return storedPaymentMethodId;
	}

	public void setStoredPaymentMethodId(final String storedPaymentMethodId)
	{
		this.storedPaymentMethodId = storedPaymentMethodId;
	}

	public boolean isApplyToAll()
	{
		return applyToAll;
	}

	public void setApplyToAll(final boolean applyToAll)
	{
		this.applyToAll = applyToAll;
	}
}
