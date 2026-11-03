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
package com.adyen.commerce.connector.service;

import java.util.List;
import java.util.Optional;

import com.adyen.commerce.connector.dto.AdyenTokenHandle;
import com.adyen.commerce.connector.dto.PaymentMethodChoice;
import com.adyen.commerce.connector.dto.PaymentMethodEnrollmentPage;
import com.adyen.commerce.connector.dto.PlatformPaymentMethod;
import com.adyen.commerce.connector.dto.PaymentMethodChangeOutcome;
import com.adyen.commerce.connector.dto.SubscriptionCancellation;
import com.adyen.commerce.connector.exception.BillingException;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;

import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.product.ProductModel;

/**
 * The single outbound entry point storefront/order code calls (the facade over the active connector).
 *
 * <p>It resolves the active connector for the order's store, enforces the merchant-account precondition,
 * builds the normalized {@code AdyenTokenHandle}, drives ensureCustomer &rarr; importToken &rarr;
 * createSubscription, persists the returned references on the SAP model, and emits SAP-side events. All
 * platform branching ends at the connector boundary.</p>
 *
 * <p>Inbound platform webhooks are handled separately by
 * {@link com.adyen.commerce.connector.webhook.SubscriptionBillingWebhookDispatcher}.</p>
 */
public interface SubscriptionBillingService
{
	/**
	 * Activate a subscription for a tokenized order: ensure the customer, import the Adyen token, resolve
	 * the plan and create the subscription on the active platform. Idempotent on the order: a second call
	 * returns the already-created reference.
	 *
	 * @param order      the placed, tokenized SAP order
	 * @param subProduct the subscription product being activated
	 * @return the persisted external subscription reference
	 */
	BillingSubscriptionRefModel activateSubscription(AbstractOrderModel order, ProductModel subProduct)
			throws BillingException;

	/**
	 * The key {@link #activateSubscription} will send to the platform for this order.
	 *
	 * <p>Part of the contract rather than an implementation detail: the attempt record written before the
	 * call has to carry the very key the call then uses.</p>
	 */
	String idempotencyKeyFor(AbstractOrderModel order);

	/**
	 * Points this subscription's future billing at a card the shopper already has vaulted with Adyen, and
	 * records locally what moved.
	 *
	 * <p>Capability-gated here rather than at the caller: a connector that advertises {@code NOT_SUPPORTED}
	 * is refused before any platform call is made. The caller is responsible for having established that
	 * the token belongs to this shopper; nothing below this line can tell.</p>
	 *
	 * @return what the platform actually did, including the scope it applied — which the caller needs in
	 *         order to say something true to the shopper
	 * @throws com.adyen.commerce.connector.exception.CapabilityUnsupportedException if the platform cannot
	 *         change the payment method of an existing subscription
	 */
	PaymentMethodChangeOutcome changePaymentMethod(BillingSubscriptionRefModel subscription,
			PaymentMethodChoice choice) throws BillingException;

	/**
	 * The payment methods this subscription's platform already holds for its customer, in that
	 * subscription's store context. Empty when the platform cannot enumerate them, when its adapter does not
	 * accept them back, or when it is not installed at all.
	 */
	List<PlatformPaymentMethod> listPaymentMethods(BillingSubscriptionRefModel subscription)
			throws BillingException;

	/**
	 * Where this subscription's customer can give the platform a payment method on a page the platform
	 * hosts. Empty when the platform offers none, when the adapter is not installed, or when the reference
	 * names no customer.
	 *
	 * <p>Asked for only when the shopper has said they want to go there: on Recurly the address is itself
	 * the credential that opens the account.</p>
	 */
	Optional<PaymentMethodEnrollmentPage> paymentMethodEnrollmentPage(BillingSubscriptionRefModel subscription)
			throws BillingException;

	/**
	 * Cancel a subscription on its platform and update the local reference.
	 *
	 * <p>The timing is part of the request rather than a default: on Recurly an immediate cancellation is a
	 * <em>terminate</em>, which ends service at once and leaves the shopper's remaining paid period
	 * unaccounted for. See {@link com.adyen.commerce.connector.dto.CancellationTiming}.</p>
	 */
	void cancel(BillingSubscriptionRefModel subscription, SubscriptionCancellation cancellation)
			throws BillingException;
}
