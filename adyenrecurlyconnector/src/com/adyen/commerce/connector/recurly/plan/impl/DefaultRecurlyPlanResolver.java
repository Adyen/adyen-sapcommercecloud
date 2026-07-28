package com.adyen.commerce.connector.recurly.plan.impl;

import java.util.List;

import com.adyen.commerce.connector.dto.PlanRef;
import com.adyen.commerce.connector.dto.PlanResolutionRequest;
import com.adyen.commerce.connector.exception.BillingException;
import com.adyen.commerce.connector.exception.PlanNotMappedException;
import com.adyen.commerce.connector.recurly.model.RecurlyPlanMappingModel;
import com.adyen.commerce.connector.recurly.plan.RecurlyPlanResolver;

import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;

/**
 * Looks up the {@code RecurlyPlanMapping} row for a SAP product code and returns its Recurly plan code.
 */
public class DefaultRecurlyPlanResolver implements RecurlyPlanResolver
{
    private FlexibleSearchService flexibleSearchService;

    @Override
    public PlanRef resolve(final PlanResolutionRequest request) throws BillingException
    {
        final FlexibleSearchQuery query = new FlexibleSearchQuery(
                "SELECT {pk} FROM {RecurlyPlanMapping} WHERE {productCode} = ?productCode");
        query.addQueryParameter("productCode", request.productCode());

        final List<RecurlyPlanMappingModel> result = flexibleSearchService
                .<RecurlyPlanMappingModel> search(query).getResult();
        if (result.isEmpty())
        {
            throw new PlanNotMappedException(
                    "No Recurly plan mapped for SAP product code '" + request.productCode() + "'");
        }

        final RecurlyPlanMappingModel mapping = result.get(0);
        return new PlanRef(mapping.getPlanCode(), mapping.getPriceId());
    }

    public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
    {
        this.flexibleSearchService = flexibleSearchService;
    }
}
