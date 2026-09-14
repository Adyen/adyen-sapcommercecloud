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

import com.adyen.commerce.connector.dto.PaymentMethodChangeScope;

/**
 * Everything the subscriptions page shows.
 *
 * <p>Two collections rather than one, because "you have no subscriptions" and "you paid for one and it
 * never started" are opposite things to be told and the second has no row to hang off. An activation that
 * failed leaves a journal entry and no reference at all, so a page built only from references shows that
 * shopper an empty list — which is the worst answer available, and the one the first draft of this page
 * gave.</p>
 */
public class SubscriptionOverviewData implements Serializable
{
	private static final long serialVersionUID = 1L;

	private List<SubscriptionEntryData> subscriptions = new ArrayList<>();

	/**
	 * Codes of orders that were paid for but whose subscription was never created and is no longer being
	 * retried. Shown as a banner naming the order, so the shopper has something to quote when they get in
	 * touch — and so they are not told they have nothing.
	 */
	private List<String> ordersAwaitingSetup = new ArrayList<>();

	public List<SubscriptionEntryData> getSubscriptions()
	{
		return subscriptions;
	}

	public void setSubscriptions(final List<SubscriptionEntryData> subscriptions)
	{
		this.subscriptions = subscriptions;
	}

	/**
	 * The subscription whose code the payment-method form should carry, or {@code null} when there is none
	 * it can use.
	 *
	 * <p>The change is per customer on Chargebee, so any one of their subscriptions identifies the customer
	 * and the store. It still cannot be "whichever is first": a reference created before the public
	 * identifier existed has none, and a form built on it posts an empty code that the facade can only
	 * refuse. Choosing here, where the rows are known, is what keeps that out of the view.</p>
	 */
	private String paymentMethodSubscriptionCode;

	/**
	 * What a change would move, as the connector behind {@link #paymentMethodSubscriptionCode} declares it,
	 * or {@code NOT_SUPPORTED} when no subscription on this page can be changed here.
	 *
	 * <p>The page needs this and not the platform's name. "This card will be used for all your
	 * subscriptions" is true of a customer-scoped change and false of a subscription-scoped one, and the
	 * only thing that decides which sentence is honest is the scope - so the scope is what travels, and the
	 * view never learns which billing platform it is looking at.</p>
	 */
	private PaymentMethodChangeScope paymentMethodChangeScope = PaymentMethodChangeScope.NOT_SUPPORTED;

	public PaymentMethodChangeScope getPaymentMethodChangeScope()
	{
		return paymentMethodChangeScope;
	}

	public void setPaymentMethodChangeScope(final PaymentMethodChangeScope paymentMethodChangeScope)
	{
		this.paymentMethodChangeScope = paymentMethodChangeScope == null
				? PaymentMethodChangeScope.NOT_SUPPORTED
				: paymentMethodChangeScope;
	}

	/**
	 * Whether anything on this page can have its payment method changed — by the control above the list or
	 * by one in a row.
	 *
	 * <p>Separate from {@link #paymentMethodChangeScope}, which describes only the page-level control. With
	 * both, "no control above the list" stops meaning "cannot be done here": a shopper whose subscriptions
	 * are all on a platform that pins the method per subscription has no control above the list and is
	 * certainly not to be told the change is unavailable.</p>
	 */
	private boolean anyPaymentMethodChangeable;

	/**
	 * Whether any subscription on this page is on a platform that offers the change <em>at all</em>,
	 * regardless of whether today's state or data allow it right now.
	 *
	 * <p>The page-wide "we can't do this online" sentence keys off this and not off
	 * {@link #anyPaymentMethodChangeable}. A subscription bought minutes ago has not been confirmed by its
	 * platform yet and is not changeable this second, but its provider certainly can change cards — telling
	 * that shopper the change is impossible would be wrong, and wrong in a way that fixes itself an hour
	 * later without anyone learning why they were told otherwise.</p>
	 */
	private boolean paymentMethodChangeSupportedSomewhere;

	public boolean isPaymentMethodChangeSupportedSomewhere()
	{
		return paymentMethodChangeSupportedSomewhere;
	}

	public void setPaymentMethodChangeSupportedSomewhere(final boolean paymentMethodChangeSupportedSomewhere)
	{
		this.paymentMethodChangeSupportedSomewhere = paymentMethodChangeSupportedSomewhere;
	}

	public boolean isAnyPaymentMethodChangeable()
	{
		return anyPaymentMethodChangeable;
	}

	public void setAnyPaymentMethodChangeable(final boolean anyPaymentMethodChangeable)
	{
		this.anyPaymentMethodChangeable = anyPaymentMethodChangeable;
	}

	public String getPaymentMethodSubscriptionCode()
	{
		return paymentMethodSubscriptionCode;
	}

	public void setPaymentMethodSubscriptionCode(final String paymentMethodSubscriptionCode)
	{
		this.paymentMethodSubscriptionCode = paymentMethodSubscriptionCode;
	}

	public List<String> getOrdersAwaitingSetup()
	{
		return ordersAwaitingSetup;
	}

	public void setOrdersAwaitingSetup(final List<String> ordersAwaitingSetup)
	{
		this.ordersAwaitingSetup = ordersAwaitingSetup;
	}

	/** True when there is genuinely nothing to say — no rows and no unfinished orders. */
	public boolean isEmpty()
	{
		return subscriptions.isEmpty() && ordersAwaitingSetup.isEmpty();
	}
}
