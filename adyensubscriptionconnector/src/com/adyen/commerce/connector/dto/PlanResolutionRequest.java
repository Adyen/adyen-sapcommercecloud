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

import java.util.Map;

/**
 * Resolves a SAP subscription product to a platform plan for one base store. The connector owns the mapping;
 * a mapping for the store wins over a mapping without a store.
 */
public record PlanResolutionRequest(String productCode, String baseStoreUid, Map<String, String> context)
{
	public PlanResolutionRequest
	{
		Dtos.requireText(productCode, "productCode");
		Dtos.requireText(baseStoreUid, "baseStoreUid");
		context = Dtos.immutableCopy(context);
	}
}
