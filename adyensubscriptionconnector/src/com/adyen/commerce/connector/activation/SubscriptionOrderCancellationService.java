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
package com.adyen.commerce.connector.activation;

import de.hybris.platform.core.model.order.OrderModel;

/**
 * Stops the subscription an order started, when that order is cancelled.
 *
 * <p>Without it the two halves disagree permanently: the order says cancelled while the billing platform
 * goes on charging the card every period. Order cancellation is also the only path in this integration that
 * reaches {@code SubscriptionBillingService.cancel}.</p>
 *
 * <p>Like {@link SubscriptionOrderActivator}, nothing escapes — a billing platform being down must not turn
 * an order cancellation into a failure — and the decision is idempotent, because an order can be announced
 * cancelled more than once.</p>
 */
public interface SubscriptionOrderCancellationService
{
	/**
	 * Cancels the subscription activated from this order, at the end of the period it has already been paid
	 * for. Does nothing, quietly, when the order started no subscription or when it has already been
	 * stopped.
	 *
	 * @param order the fully cancelled order; {@code null} is tolerated
	 */
	void cancelSubscriptionFor(OrderModel order);
}
