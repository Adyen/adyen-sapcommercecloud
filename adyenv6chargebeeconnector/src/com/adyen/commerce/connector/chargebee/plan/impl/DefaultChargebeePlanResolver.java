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
import com.adyen.commerce.connector.exception.ConnectorNotConfiguredException;
import com.adyen.commerce.connector.exception.PlanNotMappedException;

import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.store.BaseStoreModel;

/**
 * Resolves the Chargebee item price for a product in a store: the store's own {@code ChargebeePlanMapping}
 * row wins over the row without a store.
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
		final List<ChargebeePlanMappingModel> candidates = flexibleSearchService
				.<ChargebeePlanMappingModel> search(query).getResult();

		final ChargebeePlanMappingModel mapping = pick(candidates, request);
		return new PlanRef(mapping.getItemPriceId(), mapping.getPriceId());
	}

	protected ChargebeePlanMappingModel pick(final List<ChargebeePlanMappingModel> candidates,
			final PlanResolutionRequest request) throws BillingException
	{
		final List<ChargebeePlanMappingModel> forStore = candidates.stream()
				.filter(mapping -> isForStore(mapping.getBaseStore(), request.baseStoreUid())).toList();
		final List<ChargebeePlanMappingModel> defaults = candidates.stream()
				.filter(mapping -> mapping.getBaseStore() == null).toList();
		final List<ChargebeePlanMappingModel> chosen = forStore.isEmpty() ? defaults : forStore;
		if (chosen.isEmpty())
		{
			throw new PlanNotMappedException("No Chargebee item price mapped for SAP product code '"
					+ request.productCode() + "' in base store '" + request.baseStoreUid() + "'");
		}
		if (chosen.size() > 1)
		{
			throw new ConnectorNotConfiguredException("Ambiguous Chargebee plan mapping for SAP product code '"
					+ request.productCode() + "' in base store '" + request.baseStoreUid() + "': " + chosen.size()
					+ " rows");
		}
		return chosen.get(0);
	}

	protected boolean isForStore(final BaseStoreModel store, final String baseStoreUid)
	{
		return store != null && baseStoreUid.equals(store.getUid());
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}
}
