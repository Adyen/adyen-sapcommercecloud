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
import java.util.Date;

import com.adyen.commerce.connector.dto.PaymentMethodChangeScope;

/**
 * One row of the shopper's subscription list.
 *
 * <p>A view of a {@code BillingSubscriptionRef}, not a copy of it. What is absent is the point: no external
 * subscription id, no external customer or payment-method id, no platform name, no plan code and no
 * idempotency key. Several of those identify the shopper or their card on another system, one of them is
 * the SAP order code — which is sequential — and none of them means anything to the person reading the
 * page. Handing the model straight to a view is how they would all have travelled.</p>
 */
public class SubscriptionEntryData implements Serializable
{
	private static final long serialVersionUID = 1L;

	/** The opaque public identifier. The only thing that comes back from the page in a request. */
	private String code;

	/**
	 * The product's name, or its code when the product can no longer be resolved in the catalogue being
	 * served. Never the plan code: that is the billing platform's price identifier and reads like
	 * {@code test-subscription-plan-EUR-Monthly}.
	 */
	private String productName;

	private SubscriptionDisplayState state;

	/**
	 * The date the state talks about — the next renewal, the day access ends, the day it starts, or the day
	 * it ended — or {@code null} when it is not known yet. A subscription has no period until the first
	 * platform read, so this is genuinely absent for a while after purchase and the wording has to cope.
	 */
	private Date effectiveDate;

	/** Shown only when greater than one, because "quantity: 1" is noise on every other row. */
	private Integer quantity;

	/** The order this was bought on, so the shopper can find what they paid and when. May be absent. */
	private String orderCode;

	/** The date of that order. Rendered as the date; the code is a separate sentence beside it. */
	private Date orderDate;

	/**
	 * The card <em>as it was at purchase</em>, for recognition only — "the one ending 1881" — and never as
	 * something the shopper is invited to change on this row.
	 *
	 * <p>It is read from the originating order's payment info, so it does not follow a payment-method
	 * change: after the shopper moves their billing to another card this still names the old one. Where the
	 * change is offered it is offered once, above the list, because on the platform that supports it today
	 * the payment source belongs to the customer rather than to one subscription.</p>
	 */
	private String paymentMethodSummary;

	/**
	 * What a payment-method change would move <em>for this row</em>, as its own connector declares it.
	 *
	 * <p>Per row rather than per page because a shopper can hold subscriptions on more than one platform at
	 * once, and the platforms do not agree: one moves every subscription the customer has, another moves
	 * only the one it was asked about, a third cannot do it at all. A single page-level answer describes
	 * whichever row happened to be found first and silently misdescribes the rest — which is exactly how a
	 * row on a platform that cannot change its card came to sit under a control promising it could.</p>
	 */
	private PaymentMethodChangeScope paymentMethodChangeScope = PaymentMethodChangeScope.NOT_SUPPORTED;

	/**
	 * Whether the shopper can change this row's payment method right now: its platform offers it, the row
	 * is in a state where it would achieve something, and it carries a public identifier to name it by.
	 *
	 * <p>All three, because any one of them missing produces a control that can only be refused. Read by
	 * the page to decide whether to render it and re-derived by the facade when a request arrives, for the
	 * same reason the cancellation is: a page rendered minutes ago is not evidence about now.</p>
	 */
	private boolean paymentMethodChangeable;

	/**
	 * Whether some control on this page will actually move this row's payment method.
	 *
	 * <p>Not the same question as {@link #paymentMethodChangeable}, and conflating the two put a false
	 * sentence on the page. A row can be impossible to <em>name</em> in a form — it has no public code —
	 * and still be moved by the control above the list, because a customer-scoped change goes at the
	 * platform's customer record and takes every unpinned subscription with it. Such a row is not an
	 * exception to explain; it is covered, silently and correctly.</p>
	 *
	 * <p>It therefore depends on what the rest of the page offers, and is filled in after every row has
	 * been examined rather than while each one is built.</p>
	 */
	private boolean paymentMethodChangeCovered;

	/**
	 * Whether anything can be <em>done</em> to this subscription from here — as opposed to whether it can
	 * be described.
	 *
	 * <p>The two used to be one answer, and the page paid for it. Every action has to be sent to the
	 * subscription's own platform, and the credentials for that come from the base store its originating
	 * order belongs to; a reference with no order has no store and therefore nothing to reach the platform
	 * with. That was expressed by describing the row as {@code UNAVAILABLE}, which reads to the shopper as
	 * "we can't show the status of this subscription" — untrue whenever the status is perfectly well known
	 * and only the buttons are impossible.</p>
	 *
	 * <p>So the store gates this flag, and this flag gates the buttons; the state describes. Set by the
	 * facade for every row it builds, and never assumed: the default is false, because a row nobody has
	 * established a store for is a row nobody should be offering actions on.</p>
	 */
	private boolean manageable;

	public boolean isManageable()
	{
		return manageable;
	}

	public void setManageable(final boolean manageable)
	{
		this.manageable = manageable;
	}

	public boolean isPaymentMethodChangeCovered()
	{
		return paymentMethodChangeCovered;
	}

	public void setPaymentMethodChangeCovered(final boolean paymentMethodChangeCovered)
	{
		this.paymentMethodChangeCovered = paymentMethodChangeCovered;
	}

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

	public boolean isPaymentMethodChangeable()
	{
		return paymentMethodChangeable;
	}

	public void setPaymentMethodChangeable(final boolean paymentMethodChangeable)
	{
		this.paymentMethodChangeable = paymentMethodChangeable;
	}

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

	/**
	 * Whether to offer the shopper a way to stop this one. Convenience for the view, so the button condition
	 * is not a second copy of the rule.
	 *
	 * <p>The code is part of the condition, not only the state. A reference created before the public
	 * identifier existed carries none until the extension's essential data has been imported, and a button
	 * that posts an empty code cannot do anything but fail — which reads to the shopper as a subscription
	 * they are unable to cancel rather than as a deployment step somebody skipped.</p>
	 *
	 * <p>{@link #manageable} is part of it too, and is why the state no longer has to carry that job: a row
	 * we cannot reach the platform for is not describable-but-broken, it is describable and unbuttoned.</p>
	 */
	public boolean isCancellable()
	{
		return manageable && state != null && state.isCancellable() && code != null && !code.isBlank();
	}
}
