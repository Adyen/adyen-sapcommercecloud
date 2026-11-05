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
import com.adyen.commerce.connector.duplicate.PlanLookup;

/**
 * {@code SAME_PLAN_FAMILY}: plans the mapping puts in one family count as one subscription. A candidate whose
 * mapping names no family is compared by plan, exactly as {@code SAME_PLAN} would.
 *
 * <p>A record that knows only its plan (a platform listing) can be compared by plan alone: the platform does not
 * say which product, and so which family, the plan belongs to.</p>
 */
public class SamePlanFamilyRuleStrategy extends SamePlanRuleStrategy
{
	@Override
	public boolean isSameSubscription(final PlanLookup plans, final String candidateProduct,
			final String otherProduct, final String otherPlanCode)
	{
		if (super.isSameSubscription(plans, candidateProduct, otherProduct, otherPlanCode))
		{
			return true;
		}
		final String candidateFamily = familyOf(plans, candidateProduct);
		return candidateFamily != null && candidateFamily.equals(familyOf(plans, otherProduct));
	}

	/** The family the product's mapping names, or {@code null}. */
	protected String familyOf(final PlanLookup plans, final String productCode)
	{
		if (StringUtils.isBlank(productCode))
		{
			return null;
		}
		final PlanRef plan = plans.planOf(productCode);
		return plan == null ? null : StringUtils.trimToNull(plan.planFamily());
	}
}
