package com.adyen.commerce.connector.recurly.plan.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adyen.commerce.connector.dto.PlanRef;
import com.adyen.commerce.connector.dto.PlanResolutionRequest;
import com.adyen.commerce.connector.exception.PlanNotMappedException;
import com.adyen.commerce.connector.recurly.model.RecurlyPlanMappingModel;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.search.SearchResult;

@UnitTest
public class DefaultRecurlyPlanResolverTest
{
    @Mock
    private FlexibleSearchService flexibleSearchService;
    @Mock
    private SearchResult<RecurlyPlanMappingModel> searchResult;
    @Mock
    private RecurlyPlanMappingModel mapping;

    private DefaultRecurlyPlanResolver resolver;

    @Before
    public void setUp()
    {
        MockitoAnnotations.openMocks(this);
        resolver = new DefaultRecurlyPlanResolver(flexibleSearchService);
        when(flexibleSearchService.<RecurlyPlanMappingModel> search(any(FlexibleSearchQuery.class)))
                .thenReturn(searchResult);
    }

    @Test
    public void resolvesMappedPlan() throws Exception
    {
        when(searchResult.getResult()).thenReturn(List.of(mapping));
        when(mapping.getPlanCode()).thenReturn("monthly");
        when(mapping.getPriceId()).thenReturn("eur-price");

        final PlanRef result = resolver.resolve(new PlanResolutionRequest("PRODUCT", Map.of()));

        assertEquals("monthly", result.planId());
        assertEquals("eur-price", result.priceId());
    }

    @Test
    public void rejectsMissingMapping()
    {
        when(searchResult.getResult()).thenReturn(List.of());
        assertThrows(PlanNotMappedException.class,
                () -> resolver.resolve(new PlanResolutionRequest("UNKNOWN", Map.of())));
    }
}
