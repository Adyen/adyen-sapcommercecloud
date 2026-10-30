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
package com.adyen.commerce.connector.duplicate.impl;

import org.apache.commons.lang3.StringUtils;

import com.adyen.commerce.connector.dto.PlanRef;
import com.adyen.commerce.connector.duplicate.DuplicateSubscriptionRuleStrategy;
import com.adyen.commerce.connector.duplicate.PlanLookup;

/**
 * {@code SAME_PRODUCT}: the same SAP product code.
 *
 * <p>A record that does not know its product (a reference from before the code was kept, or a platform
 * listing) falls back to its plan, so it is still caught rather than let through.</p>
 */
public class SameProductRuleStrategy implements DuplicateSubscriptionRuleStrategy
{
	@Override
	public boolean isSameSubscription(final PlanLookup plans, final String candidateProduct,
			final String otherProduct, final String otherPlanCode)
	{
		if (StringUtils.isNotBlank(otherProduct))
		{
			return otherProduct.equals(candidateProduct);
		}
		final PlanRef candidate = plans.planOf(candidateProduct);
		return candidate != null && StringUtils.isNotBlank(otherPlanCode)
				&& otherPlanCode.equals(candidate.planId());
	}
}
