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
 * What the page shows for one billing platform.
 */
public class AdyenSubscriptionSectionWsDTO implements Serializable
{
	private static final long serialVersionUID = 1L;

	private String platformCode;

	private String platformName;

	private List<AdyenSubscriptionEntryWsDTO> subscriptions = new ArrayList<>();

	private List<String> ordersAwaitingSetup = new ArrayList<>();

	private List<AdyenSubscriptionChangeOfferWsDTO> paymentMethodChangeOffers = new ArrayList<>();

	private List<AdyenSubscriptionEnrollmentOfferWsDTO> paymentMethodEnrollmentOffers = new ArrayList<>();

	private boolean paymentMethodChangeSupported;

	private boolean paymentMethodChangeOffered;

	private boolean rowPaymentMethodControlOffered;

	public String getPlatformCode()
	{
		return platformCode;
	}

	public void setPlatformCode(final String platformCode)
	{
		this.platformCode = platformCode;
	}

	public String getPlatformName()
	{
		return platformName;
	}

	public void setPlatformName(final String platformName)
	{
		this.platformName = platformName;
	}

	public List<AdyenSubscriptionEntryWsDTO> getSubscriptions()
	{
		return subscriptions;
	}

	public void setSubscriptions(final List<AdyenSubscriptionEntryWsDTO> subscriptions)
	{
		this.subscriptions = subscriptions == null ? new ArrayList<>() : subscriptions;
	}

	public List<String> getOrdersAwaitingSetup()
	{
		return ordersAwaitingSetup;
	}

	public void setOrdersAwaitingSetup(final List<String> ordersAwaitingSetup)
	{
		this.ordersAwaitingSetup = ordersAwaitingSetup == null ? new ArrayList<>() : ordersAwaitingSetup;
	}

	public List<AdyenSubscriptionChangeOfferWsDTO> getPaymentMethodChangeOffers()
	{
		return paymentMethodChangeOffers;
	}

	public void setPaymentMethodChangeOffers(final List<AdyenSubscriptionChangeOfferWsDTO> paymentMethodChangeOffers)
	{
		this.paymentMethodChangeOffers = paymentMethodChangeOffers == null ? new ArrayList<>() : paymentMethodChangeOffers;
	}

	public List<AdyenSubscriptionEnrollmentOfferWsDTO> getPaymentMethodEnrollmentOffers()
	{
		return paymentMethodEnrollmentOffers;
	}

	public void setPaymentMethodEnrollmentOffers(final List<AdyenSubscriptionEnrollmentOfferWsDTO> paymentMethodEnrollmentOffers)
	{
		this.paymentMethodEnrollmentOffers = paymentMethodEnrollmentOffers == null ? new ArrayList<>() : paymentMethodEnrollmentOffers;
	}

	public boolean isPaymentMethodChangeSupported()
	{
		return paymentMethodChangeSupported;
	}

	public void setPaymentMethodChangeSupported(final boolean paymentMethodChangeSupported)
	{
		this.paymentMethodChangeSupported = paymentMethodChangeSupported;
	}

	public boolean isPaymentMethodChangeOffered()
	{
		return paymentMethodChangeOffered;
	}

	public void setPaymentMethodChangeOffered(final boolean paymentMethodChangeOffered)
	{
		this.paymentMethodChangeOffered = paymentMethodChangeOffered;
	}

	public boolean isRowPaymentMethodControlOffered()
	{
		return rowPaymentMethodControlOffered;
	}

	public void setRowPaymentMethodControlOffered(final boolean rowPaymentMethodControlOffered)
	{
		this.rowPaymentMethodControlOffered = rowPaymentMethodControlOffered;
	}
}
