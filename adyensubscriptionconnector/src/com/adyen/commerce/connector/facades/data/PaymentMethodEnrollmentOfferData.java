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
 * An invitation to give the platform a payment method on its own page, for one billing account. Only the
 * invitation: the address is minted when the shopper follows it, because on some platforms it is a
 * credential.
 */
public class PaymentMethodEnrollmentOfferData implements Serializable
{
	private static final long serialVersionUID = 1L;

	/** A subscription of the account, which the link carries so the address resolves to that account. */
	private String subscriptionCode;
	/** The store the account belongs to, for telling two offers of one section apart. */
	private String accountName;
	private PaymentMethodEnrollmentNotice notice;

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

	public PaymentMethodEnrollmentNotice getNotice()
	{
		return notice;
	}

	public void setNotice(final PaymentMethodEnrollmentNotice notice)
	{
		this.notice = notice;
	}
}
