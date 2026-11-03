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
 * <p>Deliberately separate from whatever triggers it, so the trigger can move without the rules moving
 * with it. The trigger has to fire once the Adyen token is on the order's PaymentInfo: Recurly requires a
 * network transaction id, and Chargebee cannot activate a new card that went through 3DS without it.</p>
 */
public interface SubscriptionOrderActivator
{
	/**
	 * Activates a subscription for the order when it carries a subscription product and its store runs a
	 * billing platform. Never throws: callers sit on payment and checkout paths that must not fail because
	 * a billing platform is unhappy.
	 *
	 * <p>"Carries no subscription product" and "could not tell whether it does" are not treated as the same
	 * answer. The first is the ordinary case and leaves no trace; the second leaves a journalled attempt for
	 * the retry job, because the shopper has already paid.</p>
	 *
	 * @param order the placed order; {@code null} is tolerated and does nothing
	 */
	void activateFor(OrderModel order);
}
