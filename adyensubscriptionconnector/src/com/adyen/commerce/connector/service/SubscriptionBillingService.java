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

import com.adyen.commerce.connector.dto.CancelReason;
import com.adyen.commerce.connector.exception.BillingException;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;

import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.product.ProductModel;

/**
 * The single outbound entry point storefront/order code calls (the facade over the active connector).
 *
 * <p>It resolves the active connector for the order's store, enforces the merchant-account precondition,
 * builds the normalized {@code AdyenTokenHandle}, drives ensureCustomer &rarr; importToken &rarr;
 * createSubscription, persists the returned references on the SAP model, and emits SAP-side events.
 * <b>All platform branching ends at the connector boundary.</b></p>
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
	 * Cancel a subscription on its platform and update the local reference.
	 */
	void cancel(BillingSubscriptionRefModel subscription, CancelReason reason) throws BillingException;
}
