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

import com.adyen.commerce.connector.dto.PlanRef;

/**
 * What a {@link DuplicateSubscriptionRuleStrategy} may ask about the product-to-plan mapping.
 */
@FunctionalInterface
public interface PlanLookup
{
	/**
	 * @return the plan the connector maps this product to in the store being checked, or {@code null} when the
	 *         product has none or the mapping could not be read
	 */
	PlanRef planOf(String productCode);
}
