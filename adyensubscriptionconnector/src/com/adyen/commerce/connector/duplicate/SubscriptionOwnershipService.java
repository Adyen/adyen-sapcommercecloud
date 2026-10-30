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
package com.adyen.commerce.connector.duplicate;

import java.util.Optional;

/**
 * Decides whether a customer already owns the subscription they are about to buy.
 *
 * <p>Never throws on a failing billing platform: a platform that does not answer cannot turn a purchase away,
 * so the decision falls back to what this system knows, and says so in the log.</p>
 */
public interface SubscriptionOwnershipService
{
	/**
	 * @return what blocks the purchase, or empty when nothing does. Empty also for an anonymous customer, a
	 *         store whose rule is {@code DISABLED}, and a product that is not a subscription product.
	 */
	Optional<BlockingSubscription> findBlocking(DuplicateCheckRequest request);
}
