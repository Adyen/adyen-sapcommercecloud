package com.adyen.commerce.connector.recurly.webhook;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import com.adyen.commerce.connector.dto.NormalizedBillingEvent;
import com.adyen.commerce.connector.dto.RawWebhook;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.BillingException;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;
import com.adyen.commerce.connector.recurly.client.RecurlyApiClient;
import com.adyen.commerce.connector.recurly.model.RecurlyWebhookReceiptModel;
import com.adyen.commerce.connector.registry.SubscriptionBillingConnectorRegistry;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;
import com.adyen.commerce.connector.webhook.impl.DefaultSubscriptionBillingWebhookDispatcher;

import de.hybris.platform.core.model.enumeration.EnumerationValueModel;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.type.TypeService;

/**
 * Recurly-specific dispatcher that translates the generated {@link BillingPlatform} value to its
 * persisted SAP Commerce enumeration model before executing FlexibleSearch.
 */
public class RecurlySubscriptionBillingWebhookDispatcher extends DefaultSubscriptionBillingWebhookDispatcher {
    private final FlexibleSearchService flexibleSearchService;
    private final ModelService modelService;
    private final TypeService typeService;
    private final SubscriptionBillingConnectorRegistry connectorRegistry;
    private final RecurlyApiClient apiClient;

    public RecurlySubscriptionBillingWebhookDispatcher(
            final SubscriptionBillingConnectorRegistry connectorRegistry,
            final FlexibleSearchService flexibleSearchService,
            final ModelService modelService,
            final TypeService typeService,
            final RecurlyApiClient apiClient) {
        super.setConnectorRegistry(connectorRegistry);
        super.setFlexibleSearchService(flexibleSearchService);
        super.setModelService(modelService);
        this.flexibleSearchService = flexibleSearchService;
        this.modelService = modelService;
        this.typeService = typeService;
        this.connectorRegistry = connectorRegistry;
        this.apiClient = apiClient;
    }

    @Override
    public NormalizedBillingEvent dispatch(final BillingPlatform platform, final RawWebhook raw) throws BillingException {
        final SubscriptionBillingConnector connector = connectorRegistry.getConnector(platform);
        final NormalizedBillingEvent parsed = connector.parseWebhook(raw);
        if (parsed == null) {
            return null;
        }

        final List<String> subscriptionIds;
        if (StringUtils.isNotBlank(parsed.externalSubscriptionId())) {
            subscriptionIds = List.of(parsed.externalSubscriptionId());
        } else {
            final Map<String, String> attributes = parsed.attributes();
            subscriptionIds = apiClient.resolveWebhookSubscriptionIds(attributes.get("resourceType"),
                    attributes.get("resourceId"));
        }

        for (final String subscriptionId : subscriptionIds) {
            reconcile(parsed, subscriptionId);
        }
        if (subscriptionIds.isEmpty()) {
            return parsed;
        }
        return withSubscriptionId(parsed, subscriptionIds.get(0));
    }

    protected void reconcile(final NormalizedBillingEvent event, final String externalSubscriptionId) {
        final String notificationId = StringUtils.defaultIfBlank(event.attributes().get("notificationId"),
                event.type() + "@" + event.occurredAt());
        final String notificationKey = notificationId + "|" + externalSubscriptionId;
        if (receiptExists(notificationKey)) {
            return;
        }

        final Optional<BillingSubscriptionRefModel> ref = findByExternalId(event.platform(), externalSubscriptionId);
        if (ref.isEmpty()) {
            return;
        }

        if (!newerReceiptExists(externalSubscriptionId, event.occurredAt())) {
            final String status = mapStatus(event.type());
            if (status != null) {
                ref.get().setStatus(status);
                modelService.save(ref.get());
            }
        }
        saveReceipt(notificationKey, notificationId, externalSubscriptionId, event);
    }

    protected boolean receiptExists(final String notificationKey) {
        final FlexibleSearchQuery query = new FlexibleSearchQuery(
                "SELECT {pk} FROM {RecurlyWebhookReceipt} WHERE {notificationKey} = ?notificationKey");
        query.addQueryParameter("notificationKey", notificationKey);
        query.setCount(1);
        return !flexibleSearchService.search(query).getResult().isEmpty();
    }

    protected boolean newerReceiptExists(final String externalSubscriptionId, final java.time.Instant occurredAt) {
        final FlexibleSearchQuery query = new FlexibleSearchQuery("SELECT {pk} FROM {RecurlyWebhookReceipt} "
                + "WHERE {externalSubscriptionId} = ?externalSubscriptionId AND {eventTime} > ?eventTime");
        query.addQueryParameter("externalSubscriptionId", externalSubscriptionId);
        query.addQueryParameter("eventTime", Date.from(occurredAt));
        query.setCount(1);
        return !flexibleSearchService.search(query).getResult().isEmpty();
    }

    protected void saveReceipt(final String notificationKey, final String notificationId,
                               final String externalSubscriptionId, final NormalizedBillingEvent event) {
        final RecurlyWebhookReceiptModel receipt = modelService.create(RecurlyWebhookReceiptModel.class);
        receipt.setNotificationKey(notificationKey);
        receipt.setNotificationId(notificationId);
        receipt.setExternalSubscriptionId(externalSubscriptionId);
        receipt.setEventType(event.type().name());
        receipt.setEventTime(Date.from(event.occurredAt()));
        modelService.save(receipt);
    }

    protected NormalizedBillingEvent withSubscriptionId(final NormalizedBillingEvent event,
                                                        final String externalSubscriptionId) {
        return new NormalizedBillingEvent(event.platform(), event.type(), externalSubscriptionId,
                event.externalCustomerId(), event.occurredAt(), event.attributes());
    }

    @Override
    protected Optional<BillingSubscriptionRefModel> findByExternalId(final BillingPlatform platform,
                                                                     final String externalSubscriptionId) {
        final FlexibleSearchQuery query = new FlexibleSearchQuery("SELECT {pk} FROM {BillingSubscriptionRef} "
                + "WHERE {platform} = ?platform AND {externalSubscriptionId} = ?externalSubscriptionId");
        final EnumerationValueModel platformValue = typeService.getEnumerationValue(platform.getType(),
                platform.getCode());
        query.addQueryParameter("platform", platformValue);
        query.addQueryParameter("externalSubscriptionId", externalSubscriptionId);
        final List<BillingSubscriptionRefModel> result = flexibleSearchService
                .<BillingSubscriptionRefModel>search(query).getResult();
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }
}
