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
package com.adyen.commerce.connector.dto;

/**
 * Resolved platform plan/price reference. {@code priceId} is {@code null} on platforms where the plan
 * implies the price. {@code planFamily} is the merchant-defined grouping of plans a customer should not hold
 * two of at once, or {@code null} when the mapping names none; the core never reads a vendor mapping type, so
 * the adapter passes it up here.
 */
public record PlanRef(String planId, String priceId, String planFamily)
{
	public PlanRef
	{
		Dtos.requireText(planId, "planId");
	}

	public PlanRef(final String planId, final String priceId)
	{
		this(planId, priceId, null);
	}
}
