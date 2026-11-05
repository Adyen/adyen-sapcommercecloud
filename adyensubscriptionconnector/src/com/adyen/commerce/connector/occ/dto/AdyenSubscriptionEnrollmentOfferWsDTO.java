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
 * The platform-hosted payment method page for one billing account. Carries no address: it is requested separately because it is a credential.
 */
public class AdyenSubscriptionEnrollmentOfferWsDTO implements Serializable
{
	private static final long serialVersionUID = 1L;

	private String subscriptionCode;

	private String accountName;

	private String notice;

	public String getSubscriptionCode()
	{
		return subscriptionCode;
	}

	public void setSubscriptionCode(final String subscriptionCode)
	{
		this.subscriptionCode = subscriptionCode;
	}

	public String getAccountName()
	{
		return accountName;
	}

	public void setAccountName(final String accountName)
	{
		this.accountName = accountName;
	}

	public String getNotice()
	{
		return notice;
	}

	public void setNotice(final String notice)
	{
		this.notice = notice;
	}
}
