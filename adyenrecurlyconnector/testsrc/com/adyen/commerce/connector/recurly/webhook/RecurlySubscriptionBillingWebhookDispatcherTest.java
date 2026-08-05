package com.adyen.commerce.connector.recurly.webhook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adyen.commerce.connector.dto.BillingEventType;
import com.adyen.commerce.connector.dto.NormalizedBillingEvent;
import com.adyen.commerce.connector.dto.RawWebhook;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;
import com.adyen.commerce.connector.recurly.client.RecurlyApiClient;
import com.adyen.commerce.connector.registry.SubscriptionBillingConnectorRegistry;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;

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
    private RecurlyApiClient apiClient;
    @Mock
    private EnumerationValueModel platformValue;
    @Mock
    private SearchResult<BillingSubscriptionRefModel> searchResult;
    @Mock
    private SubscriptionBillingConnector connector;
    @Mock
    private BillingSubscriptionRefModel subscriptionRef;

    private RecurlySubscriptionBillingWebhookDispatcher dispatcher;

    @Before
    public void setUp()
    {
        MockitoAnnotations.openMocks(this);
        dispatcher = new RecurlySubscriptionBillingWebhookDispatcher(connectorRegistry, flexibleSearchService,
                modelService, typeService, apiClient);
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

    @Test
    public void resolvesLightweightInvoiceAndReconcilesEverySubscription() throws Exception
    {
        final RecordingDispatcher recording = new RecordingDispatcher();
        final RawWebhook raw = new RawWebhook(Map.of(), "{}", "signature");
        final NormalizedBillingEvent parsed = event(null, BillingEventType.INVOICE_PAID,
                Map.of("resourceType", "charge_invoice", "resourceId", "number-1001"));
        when(connectorRegistry.getConnector(BillingPlatform.RECURLY)).thenReturn(connector);
        when(connector.parseWebhook(raw)).thenReturn(parsed);
        when(apiClient.resolveWebhookSubscriptionIds("charge_invoice", "number-1001"))
                .thenReturn(List.of("uuid-first", "uuid-second"));

        final NormalizedBillingEvent result = recording.dispatch(BillingPlatform.RECURLY, raw);

        assertEquals("uuid-first", result.externalSubscriptionId());
        assertEquals(List.of("uuid-first", "uuid-second"), recording.reconciled);
    }

    @Test
    public void duplicateNotificationDoesNotTouchSubscription()
    {
        final ControlledDispatcher controlled = new ControlledDispatcher();
        controlled.duplicate = true;

        controlled.reconcile(event("uuid-sub", BillingEventType.SUBSCRIPTION_CANCELLED,
                Map.of("notificationId", "notification-1")), "uuid-sub");

        verify(modelService, never()).save(any());
        assertTrue(!controlled.receiptSaved);
    }

    @Test
    public void olderNotificationIsRecordedWithoutRegressingStatus()
    {
        final ControlledDispatcher controlled = new ControlledDispatcher();
        controlled.newerReceipt = true;

        controlled.reconcile(event("uuid-sub", BillingEventType.INVOICE_PAYMENT_FAILED,
                Map.of("notificationId", "notification-2")), "uuid-sub");

        verify(subscriptionRef, never()).setStatus(any());
        verify(modelService, never()).save(subscriptionRef);
        assertTrue(controlled.receiptSaved);
    }

    @Test
    public void currentNotificationUpdatesStatusAndStoresReceipt()
    {
        final ControlledDispatcher controlled = new ControlledDispatcher();

        controlled.reconcile(event("uuid-sub", BillingEventType.INVOICE_PAYMENT_FAILED,
                Map.of("notificationId", "notification-3")), "uuid-sub");

        verify(subscriptionRef).setStatus("PAST_DUE");
        verify(modelService).save(subscriptionRef);
        assertTrue(controlled.receiptSaved);
    }

    private NormalizedBillingEvent event(final String subscriptionId, final BillingEventType type,
                                         final Map<String, String> attributes)
    {
        return new NormalizedBillingEvent(BillingPlatform.RECURLY, type, subscriptionId, "customer",
                Instant.parse("2026-07-30T10:00:00Z"), attributes);
    }

    private class RecordingDispatcher extends RecurlySubscriptionBillingWebhookDispatcher
    {
        private final List<String> reconciled = new ArrayList<>();

        RecordingDispatcher()
        {
            super(connectorRegistry, flexibleSearchService, modelService, typeService, apiClient);
        }

        @Override
        protected void reconcile(final NormalizedBillingEvent event, final String externalSubscriptionId)
        {
            reconciled.add(externalSubscriptionId);
        }
    }

    private class ControlledDispatcher extends RecurlySubscriptionBillingWebhookDispatcher
    {
        private boolean duplicate;
        private boolean newerReceipt;
        private boolean receiptSaved;

        ControlledDispatcher()
        {
            super(connectorRegistry, flexibleSearchService, modelService, typeService, apiClient);
        }

        @Override
        protected boolean receiptExists(final String notificationKey)
        {
            return duplicate;
        }

        @Override
        protected boolean newerReceiptExists(final String externalSubscriptionId, final Instant occurredAt)
        {
            return newerReceipt;
        }

        @Override
        protected Optional<BillingSubscriptionRefModel> findByExternalId(final BillingPlatform platform,
                                                                         final String externalSubscriptionId)
        {
            return Optional.of(subscriptionRef);
        }

        @Override
        protected void saveReceipt(final String notificationKey, final String notificationId,
                                   final String externalSubscriptionId, final NormalizedBillingEvent event)
        {
            receiptSaved = true;
        }
    }
}
