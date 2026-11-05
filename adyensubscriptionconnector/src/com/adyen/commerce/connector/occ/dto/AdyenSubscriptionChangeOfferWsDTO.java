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
import java.util.ArrayList;
import java.util.List;

/**
 * An account-wide payment method change on one billing account.
 */
public class AdyenSubscriptionChangeOfferWsDTO implements Serializable
{
	private static final long serialVersionUID = 1L;

	private String subscriptionCode;

	private String accountName;

	private List<AdyenSubscriptionPaymentMethodOptionWsDTO> paymentMethodOptions = new ArrayList<>();

	private List<AdyenSubscriptionPaymentMethodOptionWsDTO> adyenVaultOptions = new ArrayList<>();

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

	public List<AdyenSubscriptionPaymentMethodOptionWsDTO> getPaymentMethodOptions()
	{
		return paymentMethodOptions;
	}

	public void setPaymentMethodOptions(final List<AdyenSubscriptionPaymentMethodOptionWsDTO> paymentMethodOptions)
	{
		this.paymentMethodOptions = paymentMethodOptions == null ? new ArrayList<>() : paymentMethodOptions;
	}

	public List<AdyenSubscriptionPaymentMethodOptionWsDTO> getAdyenVaultOptions()
	{
		return adyenVaultOptions;
	}

	public void setAdyenVaultOptions(final List<AdyenSubscriptionPaymentMethodOptionWsDTO> adyenVaultOptions)
	{
		this.adyenVaultOptions = adyenVaultOptions == null ? new ArrayList<>() : adyenVaultOptions;
	}
}
