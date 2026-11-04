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
import java.util.Date;
import java.util.List;

/**
 * One subscription as the page shows it, inside the section of its own platform.
 */
public class SubscriptionEntryData implements Serializable
{
	private static final long serialVersionUID = 1L;

	/** The opaque public identifier. The only thing that comes back from the page in a request. */
	private String code;
	/**
	 * The product's name, or its code when the product cannot be resolved in the catalogue being served.
	 * Never the plan code: that is the billing platform's price identifier.
	 */
	private String productName;
	private SubscriptionDisplayState state;
	/**
	 * The date the state refers to — the next renewal, the day access ends, the day it starts or the day it
	 * ended — or {@code null} when it is not known yet. A subscription has no period until the first
	 * platform read, so this is absent for a while after purchase.
	 */
	private Date effectiveDate;
	/** Shown only when greater than one. */
	private Integer quantity;
	/** The order this was bought on, so the shopper can find what they paid and when. May be absent. */
	private String orderCode;
	/** The date of that order. */
	private Date orderDate;
	/**
	 * The card <em>as it was at purchase</em>, for recognition only. Read from the originating order's
	 * payment info, so after the shopper moves their billing to another card this still names the old one.
	 */
	private String paymentMethodSummary;
	/**
	 * Whether anything can be <em>done</em> to this subscription from here, as opposed to whether it can be
	 * described. Every action is sent with credentials of the store its originating order belongs to, so a
	 * reference with no order has nothing to reach the platform with.
	 */
	private boolean manageable;
	/**
	 * Whether the shopper can change this row's payment method right now: its platform offers it, the row is
	 * in a state where it would achieve something, and it carries a public identifier to name it by. The
	 * facade re-derives this when a request arrives, because a page rendered minutes ago is not evidence.
	 */
	private boolean paymentMethodChangeable;
	/**
	 * Whether this row carries its own control: its platform changes the payment method per subscription and
	 * holds at least two methods to choose from. One option is no choice — it is what is already billing.
	 */
	private boolean paymentMethodControl;
	/**
	 * What the platform already holds for this row's account. Offered rather than the shopper's whole vault:
	 * repointing at something the account holds imports nothing.
	 */
	private List<PaymentMethodOptionData> paymentMethodOptions = new ArrayList<>();
	/**
	 * Cards from the shopper's Adyen vault this row could be repointed at, where the platform accepts an
	 * imported token as well. Already filtered to what the platform would accept, and without cards the
	 * platform already holds under its own identifier.
	 */
	private List<PaymentMethodOptionData> adyenVaultOptions = new ArrayList<>();
	/**
	 * Whether this row's control may also offer to put the chosen method behind the shopper's other
	 * subscriptions on the same account. False when it is the only one that could move.
	 */
	private boolean paymentMethodShareable;
	/**
	 * Whether the section accounts for this row's payment method: the row can be changed itself, or the
	 * account-wide control of its own account moves it. When false while the section offers a change
	 * elsewhere, the page tells the shopper this one cannot be changed online. Filled in once every row of
	 * the section has been examined.
	 */
	private boolean paymentMethodChangeCovered;

	public String getCode()
	{
		return code;
	}

	public void setCode(final String code)
	{
		this.code = code;
	}

	public String getProductName()
	{
		return productName;
	}

	public void setProductName(final String productName)
	{
		this.productName = productName;
	}

	public SubscriptionDisplayState getState()
	{
		return state;
	}

	public void setState(final SubscriptionDisplayState state)
	{
		this.state = state;
	}

	public Date getEffectiveDate()
	{
		return effectiveDate;
	}

	public void setEffectiveDate(final Date effectiveDate)
	{
		this.effectiveDate = effectiveDate;
	}

	public Integer getQuantity()
	{
		return quantity;
	}

	public void setQuantity(final Integer quantity)
	{
		this.quantity = quantity;
	}

	public String getOrderCode()
	{
		return orderCode;
	}

	public void setOrderCode(final String orderCode)
	{
		this.orderCode = orderCode;
	}

	public Date getOrderDate()
	{
		return orderDate;
	}

	public void setOrderDate(final Date orderDate)
	{
		this.orderDate = orderDate;
	}

	public String getPaymentMethodSummary()
	{
		return paymentMethodSummary;
	}

	public void setPaymentMethodSummary(final String paymentMethodSummary)
	{
		this.paymentMethodSummary = paymentMethodSummary;
	}

	public boolean isManageable()
	{
		return manageable;
	}

	public void setManageable(final boolean manageable)
	{
		this.manageable = manageable;
	}

	public boolean isPaymentMethodChangeable()
	{
		return paymentMethodChangeable;
	}

	public void setPaymentMethodChangeable(final boolean paymentMethodChangeable)
	{
		this.paymentMethodChangeable = paymentMethodChangeable;
	}

	public boolean isPaymentMethodControl()
	{
		return paymentMethodControl;
	}

	public void setPaymentMethodControl(final boolean paymentMethodControl)
	{
		this.paymentMethodControl = paymentMethodControl;
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

	public boolean isPaymentMethodShareable()
	{
		return paymentMethodShareable;
	}

	public void setPaymentMethodShareable(final boolean paymentMethodShareable)
	{
		this.paymentMethodShareable = paymentMethodShareable;
	}

	public boolean isPaymentMethodChangeCovered()
	{
		return paymentMethodChangeCovered;
	}

	public void setPaymentMethodChangeCovered(final boolean paymentMethodChangeCovered)
	{
		this.paymentMethodChangeCovered = paymentMethodChangeCovered;
	}

	/**
	 * Whether to offer the shopper a way to stop this one, so the button condition is not a second copy of
	 * the rule. The code is part of it: a button posting an empty code can only fail.
	 */
	public boolean isCancellable()
	{
		return manageable && state != null && state.isCancellable() && code != null && !code.isBlank();
	}
}
