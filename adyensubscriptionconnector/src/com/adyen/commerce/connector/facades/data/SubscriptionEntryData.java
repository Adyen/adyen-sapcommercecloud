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

import com.adyen.commerce.connector.dto.PaymentMethodChangeScope;
import com.adyen.commerce.connector.dto.PlatformPaymentMethod;

/**
 * One row of the shopper's subscription list.
 *
 * <p>A view of a {@code BillingSubscriptionRef} rather than a copy of it: it deliberately carries no
 * external subscription, customer or payment-method id, no platform name, no plan code and no idempotency
 * key. Those identify the shopper or their card on another system, or are the sequential SAP order code,
 * and none of them means anything on the page.</p>
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
	 * The card <em>as it was at purchase</em>, for recognition only.
	 *
	 * <p>Read from the originating order's payment info, so it does not follow a payment-method change:
	 * after the shopper moves their billing to another card this still names the old one.</p>
	 */
	private String paymentMethodSummary;

	/**
	 * What a payment-method change would move <em>for this row</em>, as its own connector declares it.
	 *
	 * <p>Per row rather than per page because a shopper can hold subscriptions on more than one platform at
	 * once and the platforms do not agree: one moves every subscription the customer has, another moves only
	 * the one it was asked about, a third cannot do it at all.</p>
	 */
	private PaymentMethodChangeScope paymentMethodChangeScope = PaymentMethodChangeScope.NOT_SUPPORTED;

	/**
	 * Whether the shopper can change this row's payment method right now: its platform offers it, the row is
	 * in a state where it would achieve something, and it carries a public identifier to name it by.
	 *
	 * <p>Read by the page to decide whether to render the control, and re-derived by the facade when a
	 * request arrives, because a page rendered minutes ago is not evidence about now.</p>
	 */
	private boolean paymentMethodChangeable;

	/**
	 * What this row's own control may offer, for a platform that pins the method to one subscription.
	 *
	 * <p>These are the platform's methods, not the shopper's Adyen vault: a subscription-scoped change
	 * repoints at something the billing account already holds, and importing a freshly chosen card is a
	 * different operation that platform may not accept. Empty for every other kind of row.</p>
	 */
	private transient List<PlatformPaymentMethod> paymentMethodOptions = new ArrayList<>();

	public List<PlatformPaymentMethod> getPaymentMethodOptions()
	{
		return paymentMethodOptions;
	}

	public void setPaymentMethodOptions(final List<PlatformPaymentMethod> paymentMethodOptions)
	{
		this.paymentMethodOptions = paymentMethodOptions == null ? new ArrayList<>() : paymentMethodOptions;
	}

	/**
	 * The identifier, among this row's options, of the method the subscription is billed to right now.
	 *
	 * <p>Resolved by the row's own connector from the reference it stored, because only the adapter knows
	 * how that reference is encoded. Distinct from {@code PlatformPaymentMethod.defaultForCustomer}, which
	 * is the platform's default for the whole account and answers a different question - on a platform that
	 * pins a method per subscription the two are routinely different instruments.</p>
	 */
	private String currentPaymentMethodId;

	public String getCurrentPaymentMethodId()
	{
		return currentPaymentMethodId;
	}

	public void setCurrentPaymentMethodId(final String currentPaymentMethodId)
	{
		this.currentPaymentMethodId = currentPaymentMethodId;
	}

	/**
	 * Cards from the shopper's Adyen vault this row could be repointed at, for a platform that accepts an
	 * imported token as well as its own stored methods.
	 *
	 * <p>Already filtered: a platform that charges an imported token as a merchant-initiated transaction is
	 * refused one carrying no network transaction id, so such a card never reaches this list.</p>
	 */
	private transient List<PlatformPaymentMethod> adyenVaultOptions = new ArrayList<>();

	public List<PlatformPaymentMethod> getAdyenVaultOptions()
	{
		return adyenVaultOptions;
	}

	public void setAdyenVaultOptions(final List<PlatformPaymentMethod> adyenVaultOptions)
	{
		this.adyenVaultOptions = adyenVaultOptions == null ? new ArrayList<>() : adyenVaultOptions;
	}

	/**
	 * Whether some control on this page will actually move this row's payment method.
	 *
	 * <p>Not the same question as {@link #paymentMethodChangeable}: a row with no public code cannot be
	 * <em>named</em> in a form and is still moved by the control above the list, because a customer-scoped
	 * change goes at the platform's customer record and takes every unpinned subscription with it. It
	 * therefore depends on what the rest of the page offers, and is filled in once every row has been
	 * examined rather than while each one is built.</p>
	 */
	private boolean paymentMethodChangeCovered;

	/**
	 * Whether anything can be <em>done</em> to this subscription from here, as opposed to whether it can be
	 * described.
	 *
	 * <p>Every action is sent to the subscription's own platform with credentials taken from the base store
	 * its originating order belongs to, so a reference with no order has nothing to reach the platform with.
	 * The store gates this flag and this flag gates the buttons, while the state only describes. Defaults to
	 * false and is set by the facade for every row it builds.</p>
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
	 * <p>The code is part of the condition as much as the state is: a reference carries none until the
	 * extension's essential data has been imported, and a button that posts an empty code can only fail.</p>
	 */
	public boolean isCancellable()
	{
		return manageable && state != null && state.isCancellable() && code != null && !code.isBlank();
	}
}
