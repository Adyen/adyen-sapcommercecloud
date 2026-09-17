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
 * <p>Two collections rather than one: an activation that failed leaves a journal entry and no reference at
 * all, so "you have no subscriptions" and "you paid for one and it never started" have to be told apart.</p>
 */
public class SubscriptionOverviewData implements Serializable
{
	private static final long serialVersionUID = 1L;

	private List<SubscriptionEntryData> subscriptions = new ArrayList<>();

	/**
	 * Codes of orders that were paid for but whose subscription was never created and is not being retried.
	 * Shown as a banner naming the order, so the shopper has something to quote when they get in touch.
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
	 * and the store — but not simply the first, because a reference without a public identifier would build
	 * a form posting an empty code that the facade can only refuse.</p>
	 */
	private String paymentMethodSubscriptionCode;

	/**
	 * What a change would move, as the connector behind {@link #paymentMethodSubscriptionCode} declares it,
	 * or {@code NOT_SUPPORTED} when no subscription on this page can be changed here.
	 *
	 * <p>The scope and not the platform's name is what picks the sentence the page prints, so the view never
	 * learns which billing platform it is looking at.</p>
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
	 * <p>Separate from {@link #paymentMethodChangeScope}, which describes only the page-level control: a
	 * shopper whose subscriptions all pin the method per subscription has no control above the list and must
	 * still not be told the change is unavailable.</p>
	 */
	private boolean anyPaymentMethodChangeable;

	/**
	 * Whether any subscription on this page is on a platform that offers the change <em>at all</em>,
	 * regardless of whether today's state or data allow it right now.
	 *
	 * <p>The page-wide "we can't do this online" sentence keys off this and not off
	 * {@link #anyPaymentMethodChangeable}: a subscription bought minutes ago is not changeable this second,
	 * yet its provider can change cards, and the sentence would stop being true after the first
	 * reconciliation.</p>
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

	/**
	 * Whether at least one row will render a control of its own.
	 *
	 * <p>{@link #anyPaymentMethodChangeable} asks whether a change is possible; this asks whether the shopper
	 * can see somewhere to make it. A subscription-scoped row is changeable and still shows nothing when its
	 * platform holds no second method to move to.</p>
	 */
	private boolean anyRowPaymentMethodControl;

	public boolean isAnyRowPaymentMethodControl()
	{
		return anyRowPaymentMethodControl;
	}

	public void setAnyRowPaymentMethodControl(final boolean anyRowPaymentMethodControl)
	{
		this.anyRowPaymentMethodControl = anyRowPaymentMethodControl;
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
