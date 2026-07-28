package com.adyen.commerce.connector.recurly.webhook;

import java.util.List;
import java.util.Optional;

import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;
import com.adyen.commerce.connector.webhook.impl.DefaultSubscriptionBillingWebhookDispatcher;

import de.hybris.platform.core.model.enumeration.EnumerationValueModel;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.type.TypeService;

/**
 * Recurly-specific dispatcher that translates the generated {@link BillingPlatform} value to its
 * persisted SAP Commerce enumeration model before executing FlexibleSearch.
 */
public class RecurlySubscriptionBillingWebhookDispatcher extends DefaultSubscriptionBillingWebhookDispatcher
{
    private FlexibleSearchService flexibleSearchService;
    private TypeService typeService;

    @Override
    protected Optional<BillingSubscriptionRefModel> findByExternalId(final BillingPlatform platform,
                                                                     final String externalSubscriptionId)
    {
        final FlexibleSearchQuery query = new FlexibleSearchQuery("SELECT {pk} FROM {BillingSubscriptionRef} "
                + "WHERE {platform} = ?platform AND {externalSubscriptionId} = ?externalSubscriptionId");
        final EnumerationValueModel platformValue = typeService.getEnumerationValue(platform.getType(), platform.getCode());
        query.addQueryParameter("platform", platformValue);
        query.addQueryParameter("externalSubscriptionId", externalSubscriptionId);
        final List<BillingSubscriptionRefModel> result = flexibleSearchService
                .<BillingSubscriptionRefModel> search(query).getResult();
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    @Override
    public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
    {
        super.setFlexibleSearchService(flexibleSearchService);
        this.flexibleSearchService = flexibleSearchService;
    }

    public void setTypeService(final TypeService typeService)
    {
        this.typeService = typeService;
    }
}
