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
 * Decides whether a placed order should become a subscription and, if so, activates exactly one.
 *
 * <p>Deliberately separate from whatever triggers it. The first attempt hung this off a place-order
 * hook and had to be moved: at that point the Adyen token is not yet on the order's PaymentInfo, so
 * Recurly (which requires a network transaction id) could never activate and Chargebee failed for any
 * new card going through 3DS. Keeping the decision here means the trigger can move again without the
 * rules moving with it.</p>
 */
public interface SubscriptionOrderActivator
{
	/**
	 * Activates a subscription for the order when it carries a subscription product and its store runs a
	 * billing platform. Never throws: callers sit on payment and checkout paths that must not fail because
	 * a billing platform is unhappy.
	 *
	 * <p>"Carries no subscription product" and "we could not tell whether it does" are not the same answer
	 * and are not treated as one. The first is the ordinary case and leaves no trace; the second leaves a
	 * journalled attempt for the retry job, because the shopper has already paid and an order that quietly
	 * turns out to have been a subscription after all would otherwise have nothing to retry from.</p>
	 *
	 * @param order the placed order; {@code null} is tolerated and does nothing
	 */
	void activateFor(OrderModel order);
}
