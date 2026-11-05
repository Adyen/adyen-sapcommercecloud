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

/**
 * One meaning of "the same subscription", bound to one value of {@code DuplicateSubscriptionRule}.
 *
 * <p>Asked the same question about three kinds of record, which know different things: a local reference knows
 * its product and the plan it ended up on, an order entry knows only its product, and a platform listing knows
 * only the plan. A strategy compares what both sides have.</p>
 */
public interface DuplicateSubscriptionRuleStrategy
{
	/**
	 * @param plans            the mapping, for whatever a side lacks
	 * @param candidateProduct the product being bought
	 * @param otherProduct     the product of the existing record; {@code null} when it does not know one
	 * @param otherPlanCode    the plan of the existing record, in the candidate's platform; {@code null} when it
	 *                         does not know one, or it is on another platform
	 * @return whether buying the candidate would be buying the other record's subscription again
	 */
	boolean isSameSubscription(PlanLookup plans, String candidateProduct, String otherProduct, String otherPlanCode);
}
