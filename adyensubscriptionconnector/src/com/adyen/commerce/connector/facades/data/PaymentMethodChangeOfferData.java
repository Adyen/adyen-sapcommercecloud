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
import java.util.ArrayList;
import java.util.List;

/**
 * A control that changes the payment method of a whole billing account: every subscription the account holds
 * on the platform moves with it. Offered where the platform's change is account-wide rather than per
 * subscription; one per account, since a shopper can hold accounts in several stores of one platform.
 */
public class PaymentMethodChangeOfferData implements Serializable
{
	private static final long serialVersionUID = 1L;

	/**
	 * A subscription of the account, which the form posts so the facade can establish the customer, the
	 * account and its store. Any of them identifies the account.
	 */
	private String subscriptionCode;
	/** The store the account belongs to, for telling two offers of one section apart. */
	private String accountName;
	/** What the platform already holds for the account. */
	private List<PaymentMethodOptionData> paymentMethodOptions = new ArrayList<>();
	/** Cards from the shopper's Adyen vault the platform would accept, without those it already holds. */
	private List<PaymentMethodOptionData> adyenVaultOptions = new ArrayList<>();

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

	public List<PaymentMethodOptionData> getPaymentMethodOptions()
	{
		return paymentMethodOptions;
	}

	public void setPaymentMethodOptions(final List<PaymentMethodOptionData> paymentMethodOptions)
	{
		this.paymentMethodOptions = paymentMethodOptions == null ? new ArrayList<>() : paymentMethodOptions;
	}

	public List<PaymentMethodOptionData> getAdyenVaultOptions()
	{
		return adyenVaultOptions;
	}

	public void setAdyenVaultOptions(final List<PaymentMethodOptionData> adyenVaultOptions)
	{
		this.adyenVaultOptions = adyenVaultOptions == null ? new ArrayList<>() : adyenVaultOptions;
	}
}
