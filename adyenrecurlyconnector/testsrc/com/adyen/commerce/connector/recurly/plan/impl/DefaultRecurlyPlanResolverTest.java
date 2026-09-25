package com.adyen.commerce.connector.recurly.plan.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adyen.commerce.connector.dto.PlanRef;
import com.adyen.commerce.connector.dto.PlanResolutionRequest;
import com.adyen.commerce.connector.exception.ConnectorNotConfiguredException;
import com.adyen.commerce.connector.exception.PlanNotMappedException;
import com.adyen.commerce.connector.recurly.model.RecurlyPlanMappingModel;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.search.SearchResult;
import de.hybris.platform.store.BaseStoreModel;

@UnitTest
public class DefaultRecurlyPlanResolverTest
{
    private static final String STORE = "electronics";

    @Mock
    private FlexibleSearchService flexibleSearchService;
    @Mock
    private SearchResult<RecurlyPlanMappingModel> searchResult;

    private DefaultRecurlyPlanResolver resolver;

    @Before
    public void setUp()
    {
        MockitoAnnotations.openMocks(this);
        resolver = new DefaultRecurlyPlanResolver();
        resolver.setFlexibleSearchService(flexibleSearchService);
        when(flexibleSearchService.<RecurlyPlanMappingModel> search(any(FlexibleSearchQuery.class)))
                .thenReturn(searchResult);
    }

    @Test
    public void resolvesMappedPlan() throws Exception
    {
        givenMappings(mapping(null, "monthly", "eur-price"));

        final PlanRef result = resolver.resolve(request("PRODUCT"));

        assertEquals("monthly", result.planId());
        assertEquals("eur-price", result.priceId());
        final ArgumentCaptor<FlexibleSearchQuery> query = ArgumentCaptor.forClass(FlexibleSearchQuery.class);
        verify(flexibleSearchService).search(query.capture());
        assertEquals("PRODUCT", query.getValue().getQueryParameters().get("productCode"));
    }

    @Test
    public void theStoresOwnMappingWinsOverTheDefault() throws Exception
    {
        givenMappings(mapping(null, "default-plan", null),
                mapping(STORE, "store-plan", null), mapping("apparel", "other-plan", null));

        assertEquals("store-plan", resolver.resolve(request("PRODUCT")).planId());
    }

    @Test
    public void fallsBackToTheMappingWithoutAStore() throws Exception
    {
        givenMappings(mapping("apparel", "other-plan", null),
                mapping(null, "default-plan", null));

        assertEquals("default-plan", resolver.resolve(request("PRODUCT")).planId());
    }

    @Test
    public void anotherStoresMappingDoesNotApply()
    {
        givenMappings(mapping("apparel", "other-plan", null));

        final PlanNotMappedException error = assertThrows(PlanNotMappedException.class,
                () -> resolver.resolve(request("PRODUCT")));
        assertTrue(error.getMessage().contains(STORE));
    }

    @Test
    public void rejectsMissingMapping()
    {
        when(searchResult.getResult()).thenReturn(List.of());
        assertThrows(PlanNotMappedException.class, () -> resolver.resolve(request("UNKNOWN")));
    }

    /** Two candidates for one store are a configuration error, not a choice to make at random. */
    @Test
    public void refusesAnAmbiguousMapping()
    {
        givenMappings(mapping(STORE, "plan-a", null), mapping(STORE, "plan-b", null));

        assertThrows(ConnectorNotConfiguredException.class, () -> resolver.resolve(request("PRODUCT")));
    }

    /** Built before stubbing: creating the mocks inside {@code thenReturn} would nest stubbings. */
    private void givenMappings(final RecurlyPlanMappingModel... mappings)
    {
        when(searchResult.getResult()).thenReturn(List.of(mappings));
    }

    private static PlanResolutionRequest request(final String productCode)
    {
        return new PlanResolutionRequest(productCode, STORE, Map.of());
    }

    private static RecurlyPlanMappingModel mapping(final String storeUid, final String planCode, final String priceId)
    {
        final RecurlyPlanMappingModel mapping = mock(RecurlyPlanMappingModel.class);
        if (storeUid != null)
        {
            final BaseStoreModel store = mock(BaseStoreModel.class);
            when(store.getUid()).thenReturn(storeUid);
            when(mapping.getBaseStore()).thenReturn(store);
        }
        when(mapping.getPlanCode()).thenReturn(planCode);
        when(mapping.getPriceId()).thenReturn(priceId);
        return mapping;
    }
}
