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
package com.adyen.commerce.connector.chargebee.plan.impl;

import java.util.List;

import com.adyen.commerce.connector.chargebee.model.ChargebeePlanMappingModel;
import com.adyen.commerce.connector.chargebee.plan.ChargebeePlanResolver;
import com.adyen.commerce.connector.dto.PlanRef;
import com.adyen.commerce.connector.dto.PlanResolutionRequest;
import com.adyen.commerce.connector.exception.BillingException;
import com.adyen.commerce.connector.exception.PlanNotMappedException;

import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;

/**
 * Looks up the {@code ChargebeePlanMapping} row for a SAP product code and returns its item price id.
 */
public class DefaultChargebeePlanResolver implements ChargebeePlanResolver
{
	private FlexibleSearchService flexibleSearchService;

	@Override
	public PlanRef resolve(final PlanResolutionRequest request) throws BillingException
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery(
				"SELECT {pk} FROM {ChargebeePlanMapping} WHERE {productCode} = ?productCode");
		query.addQueryParameter("productCode", request.productCode());

		final List<ChargebeePlanMappingModel> result = flexibleSearchService
				.<ChargebeePlanMappingModel> search(query).getResult();
		if (result.isEmpty())
		{
			throw new PlanNotMappedException(
					"No Chargebee item price mapped for SAP product code '" + request.productCode() + "'");
		}

		final ChargebeePlanMappingModel mapping = result.get(0);
		return new PlanRef(mapping.getItemPriceId(), mapping.getPriceId());
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}
}
