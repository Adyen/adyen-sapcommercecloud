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
 * Everything the page shows for one billing platform: its subscriptions and the controls that act on them.
 *
 * <p>Nothing in a section refers to another platform. Its controls are built from its own connector's
 * declarations, its forms post its own platform code, and the facade refuses a request whose subscription is
 * not on that platform.</p>
 */
public class SubscriptionPlatformSectionData implements Serializable
{
	private static final long serialVersionUID = 1L;

	/** The platform's code, posted back by every form of the section. */
	private String platformCode;
	/** The platform's localized name as its connector extension declares it. */
	private String platformName;
	private List<SubscriptionEntryData> subscriptions = new ArrayList<>();
	/**
	 * Codes of orders on this platform that were paid for but whose subscription was never created and is
	 * not being retried, so the shopper has something to quote when they get in touch.
	 */
	private List<String> ordersAwaitingSetup = new ArrayList<>();
	/** One per billing account whose payment method changes as a whole; empty where changes are per row. */
	private List<PaymentMethodChangeOfferData> paymentMethodChangeOffers = new ArrayList<>();
	/** One per billing account whose platform hosts a payment-method page. */
	private List<PaymentMethodEnrollmentOfferData> paymentMethodEnrollmentOffers = new ArrayList<>();
	/**
	 * Whether the platform can change a payment method at all for some subscription here, regardless of
	 * whether today's state allows it: a subscription bought minutes ago is not changeable yet, and its
	 * provider must still not be described as incapable.
	 */
	private boolean paymentMethodChangeSupported;
	/** Whether at least one row carries a control of its own. */
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

	public List<SubscriptionEntryData> getSubscriptions()
	{
		return subscriptions;
	}

	public void setSubscriptions(final List<SubscriptionEntryData> subscriptions)
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

	public List<PaymentMethodChangeOfferData> getPaymentMethodChangeOffers()
	{
		return paymentMethodChangeOffers;
	}

	public void setPaymentMethodChangeOffers(final List<PaymentMethodChangeOfferData> offers)
	{
		this.paymentMethodChangeOffers = offers == null ? new ArrayList<>() : offers;
	}

	public List<PaymentMethodEnrollmentOfferData> getPaymentMethodEnrollmentOffers()
	{
		return paymentMethodEnrollmentOffers;
	}

	public void setPaymentMethodEnrollmentOffers(final List<PaymentMethodEnrollmentOfferData> offers)
	{
		this.paymentMethodEnrollmentOffers = offers == null ? new ArrayList<>() : offers;
	}

	public boolean isPaymentMethodChangeSupported()
	{
		return paymentMethodChangeSupported;
	}

	public void setPaymentMethodChangeSupported(final boolean paymentMethodChangeSupported)
	{
		this.paymentMethodChangeSupported = paymentMethodChangeSupported;
	}

	public boolean isRowPaymentMethodControlOffered()
	{
		return rowPaymentMethodControlOffered;
	}

	public void setRowPaymentMethodControlOffered(final boolean rowPaymentMethodControlOffered)
	{
		this.rowPaymentMethodControlOffered = rowPaymentMethodControlOffered;
	}

	/** Whether some control in this section changes a payment method: account-wide or in a row. */
	public boolean isPaymentMethodChangeOffered()
	{
		return !paymentMethodChangeOffers.isEmpty() || rowPaymentMethodControlOffered;
	}
}
