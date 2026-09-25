package com.adyen.commerce.connector.recurly.plan.impl;

import java.util.List;

import com.adyen.commerce.connector.dto.PlanRef;
import com.adyen.commerce.connector.dto.PlanResolutionRequest;
import com.adyen.commerce.connector.exception.BillingException;
import com.adyen.commerce.connector.exception.ConnectorNotConfiguredException;
import com.adyen.commerce.connector.exception.PlanNotMappedException;
import com.adyen.commerce.connector.recurly.model.RecurlyPlanMappingModel;
import com.adyen.commerce.connector.recurly.plan.RecurlyPlanResolver;

import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.store.BaseStoreModel;

/**
 * Resolves the Recurly plan code for a product in a store: the store's own {@code RecurlyPlanMapping} row
 * wins over the row without a store.
 */
public class DefaultRecurlyPlanResolver implements RecurlyPlanResolver {
    private FlexibleSearchService flexibleSearchService;

    @Override
    public PlanRef resolve(final PlanResolutionRequest request) throws BillingException {
        final FlexibleSearchQuery query = new FlexibleSearchQuery(
                "SELECT {pk} FROM {RecurlyPlanMapping} WHERE {productCode} = ?productCode");
        query.addQueryParameter("productCode", request.productCode());
        final List<RecurlyPlanMappingModel> candidates = flexibleSearchService
                .<RecurlyPlanMappingModel>search(query).getResult();

        final RecurlyPlanMappingModel mapping = pick(candidates, request);
        return new PlanRef(mapping.getPlanCode(), mapping.getPriceId());
    }

    protected RecurlyPlanMappingModel pick(final List<RecurlyPlanMappingModel> candidates,
                                           final PlanResolutionRequest request) throws BillingException {
        final List<RecurlyPlanMappingModel> forStore = candidates.stream()
                .filter(mapping -> isForStore(mapping.getBaseStore(), request.baseStoreUid())).toList();
        final List<RecurlyPlanMappingModel> defaults = candidates.stream()
                .filter(mapping -> mapping.getBaseStore() == null).toList();
        final List<RecurlyPlanMappingModel> chosen = forStore.isEmpty() ? defaults : forStore;
        if (chosen.isEmpty()) {
            throw new PlanNotMappedException("No Recurly plan mapped for SAP product code '" + request.productCode()
                    + "' in base store '" + request.baseStoreUid() + "'");
        }
        if (chosen.size() > 1) {
            throw new ConnectorNotConfiguredException("Ambiguous Recurly plan mapping for SAP product code '"
                    + request.productCode() + "' in base store '" + request.baseStoreUid() + "': " + chosen.size()
                    + " rows");
        }
        return chosen.get(0);
    }

    protected boolean isForStore(final BaseStoreModel store, final String baseStoreUid) {
        return store != null && baseStoreUid.equals(store.getUid());
    }

    public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService) {
        this.flexibleSearchService = flexibleSearchService;
    }
}
