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
 * {@code SAME_PLAN}: the same platform plan, whichever product it was bought through.
 */
public class SamePlanRuleStrategy implements DuplicateSubscriptionRuleStrategy
{
	@Override
	public boolean isSameSubscription(final PlanLookup plans, final String candidateProduct,
			final String otherProduct, final String otherPlanCode)
	{
		final String candidatePlan = planCodeOf(plans, candidateProduct);
		final String otherPlan = StringUtils.isNotBlank(otherPlanCode) ? otherPlanCode
				: planCodeOf(plans, otherProduct);
		return candidatePlan != null && candidatePlan.equals(otherPlan);
	}

	/** The plan the product maps to, or {@code null}. */
	protected String planCodeOf(final PlanLookup plans, final String productCode)
	{
		if (StringUtils.isBlank(productCode))
		{
			return null;
		}
		final PlanRef plan = plans.planOf(productCode);
		return plan == null ? null : plan.planId();
	}
}
