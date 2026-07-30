package com.adyen.commerce.connector.recurly.webhook;

import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;
import com.adyen.commerce.connector.registry.SubscriptionBillingConnectorRegistry;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.model.enumeration.EnumerationValueModel;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.search.SearchResult;
import de.hybris.platform.servicelayer.type.TypeService;

@UnitTest
public class RecurlySubscriptionBillingWebhookDispatcherTest
{
    @Mock
    private FlexibleSearchService flexibleSearchService;
    @Mock
    private TypeService typeService;
    @Mock
    private SubscriptionBillingConnectorRegistry connectorRegistry;
    @Mock
    private ModelService modelService;
    @Mock
    private EnumerationValueModel platformValue;
    @Mock
    private SearchResult<BillingSubscriptionRefModel> searchResult;

    private RecurlySubscriptionBillingWebhookDispatcher dispatcher;

    @Before
    public void setUp()
    {
        MockitoAnnotations.openMocks(this);
        dispatcher = new RecurlySubscriptionBillingWebhookDispatcher(connectorRegistry, flexibleSearchService,
                modelService, typeService);
    }

    @Test
    public void usesPersistedEnumerationValueForPlatformQuery()
    {
        when(typeService.getEnumerationValue(BillingPlatform.RECURLY.getType(),
                BillingPlatform.RECURLY.getCode())).thenReturn(platformValue);
        when(flexibleSearchService.<BillingSubscriptionRefModel> search(any(FlexibleSearchQuery.class)))
                .thenReturn(searchResult);
        when(searchResult.getResult()).thenReturn(Collections.emptyList());

        dispatcher.findByExternalId(BillingPlatform.RECURLY, "uuid-local-test");

        final ArgumentCaptor<FlexibleSearchQuery> queryCaptor = ArgumentCaptor.forClass(FlexibleSearchQuery.class);
        verify(typeService).getEnumerationValue(BillingPlatform.RECURLY.getType(),
                BillingPlatform.RECURLY.getCode());
        verify(flexibleSearchService).search(queryCaptor.capture());
        assertSame(platformValue, queryCaptor.getValue().getQueryParameters().get("platform"));
    }
}
