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
package com.adyen.commerce.connector.webhook.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adyen.commerce.connector.dto.BillingEventType;
import com.adyen.commerce.connector.dto.NormalizedBillingEvent;
import com.adyen.commerce.connector.dto.RawWebhook;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;
import com.adyen.commerce.connector.registry.SubscriptionBillingConnectorRegistry;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.search.SearchResult;

/**
 * Unit test for {@link DefaultSubscriptionBillingWebhookDispatcher} (task P1.10): dispatch delegates to
 * the platform-resolved connector's {@code parseWebhook}, then reconciles the local
 * {@code BillingSubscriptionRef} status from the normalized event type. Full reconciliation (out-of-order,
 * duplicate, replay handling) is design P4.1 and intentionally not covered here.
 */
@UnitTest
public class DefaultSubscriptionBillingWebhookDispatcherTest
{
	@Mock
	private SubscriptionBillingConnectorRegistry connectorRegistry;
	@Mock
	private FlexibleSearchService flexibleSearchService;
	@Mock
	private ModelService modelService;
	@Mock
	private SubscriptionBillingConnector connector;
	@Mock
	private BillingSubscriptionRefModel refModel;
	@Mock
	private SearchResult<BillingSubscriptionRefModel> searchResult;

	private DefaultSubscriptionBillingWebhookDispatcher dispatcher;
	private RawWebhook raw;

	@Before
	public void setUp() throws Exception
	{
		MockitoAnnotations.openMocks(this);
		dispatcher = new DefaultSubscriptionBillingWebhookDispatcher();
		dispatcher.setConnectorRegistry(connectorRegistry);
		dispatcher.setFlexibleSearchService(flexibleSearchService);
		dispatcher.setModelService(modelService);

		raw = new RawWebhook(Map.of(), "{}", null);
		when(connectorRegistry.getConnector(BillingPlatform.CHARGEBEE)).thenReturn(connector);
		when(flexibleSearchService.<BillingSubscriptionRefModel> search(any(FlexibleSearchQuery.class)))
				.thenReturn(searchResult);
	}

	@Test
	public void dispatchDelegatesToPlatformResolvedConnector() throws Exception
	{
		final NormalizedBillingEvent event = eventOf(BillingEventType.SUBSCRIPTION_ACTIVATED, "sub-1");
		when(connector.parseWebhook(raw)).thenReturn(event);
		when(searchResult.getResult()).thenReturn(Collections.emptyList());

		final NormalizedBillingEvent result = dispatcher.dispatch(BillingPlatform.CHARGEBEE, raw);

		assertSame(event, result);
		verify(connectorRegistry).getConnector(BillingPlatform.CHARGEBEE);
	}

	@Test
	public void dispatchReturnsNullWhenConnectorHasNoInterestingEvent() throws Exception
	{
		when(connector.parseWebhook(raw)).thenReturn(null);

		final NormalizedBillingEvent result = dispatcher.dispatch(BillingPlatform.CHARGEBEE, raw);

		assertNull(result);
		verify(modelService, never()).save(any());
	}

	@Test
	public void dispatchIgnoresEventForUnknownSubscription() throws Exception
	{
		when(connector.parseWebhook(raw)).thenReturn(eventOf(BillingEventType.SUBSCRIPTION_ACTIVATED, "sub-unknown"));
		when(searchResult.getResult()).thenReturn(Collections.emptyList());

		dispatcher.dispatch(BillingPlatform.CHARGEBEE, raw);

		verify(modelService, never()).save(any());
	}

	@Test
	public void dispatchUpdatesStatusToActiveOnSubscriptionActivated() throws Exception
	{
		assertReconciledStatus(BillingEventType.SUBSCRIPTION_ACTIVATED, "ACTIVE");
	}

	@Test
	public void dispatchUpdatesStatusToActiveOnSubscriptionRenewed() throws Exception
	{
		assertReconciledStatus(BillingEventType.SUBSCRIPTION_RENEWED, "ACTIVE");
	}

	@Test
	public void dispatchUpdatesStatusToActiveOnSubscriptionResumed() throws Exception
	{
		assertReconciledStatus(BillingEventType.SUBSCRIPTION_RESUMED, "ACTIVE");
	}

	@Test
	public void dispatchUpdatesStatusToActiveOnInvoicePaid() throws Exception
	{
		assertReconciledStatus(BillingEventType.INVOICE_PAID, "ACTIVE");
	}

	@Test
	public void dispatchUpdatesStatusToCancelledOnSubscriptionCancelled() throws Exception
	{
		assertReconciledStatus(BillingEventType.SUBSCRIPTION_CANCELLED, "CANCELLED");
	}

	@Test
	public void dispatchUpdatesStatusToPausedOnSubscriptionPaused() throws Exception
	{
		assertReconciledStatus(BillingEventType.SUBSCRIPTION_PAUSED, "PAUSED");
	}

	@Test
	public void dispatchUpdatesStatusToPastDueOnInvoicePaymentFailed() throws Exception
	{
		assertReconciledStatus(BillingEventType.INVOICE_PAYMENT_FAILED, "PAST_DUE");
	}

	@Test
	public void dispatchDoesNotSaveForUnmappedEventType() throws Exception
	{
		when(connector.parseWebhook(raw)).thenReturn(eventOf(BillingEventType.PAYMENT_METHOD_UPDATED, "sub-1"));
		when(searchResult.getResult()).thenReturn(List.of(refModel));

		dispatcher.dispatch(BillingPlatform.CHARGEBEE, raw);

		verify(modelService, never()).save(any());
	}

	private void assertReconciledStatus(final BillingEventType type, final String expectedStatus) throws Exception
	{
		when(connector.parseWebhook(raw)).thenReturn(eventOf(type, "sub-1"));
		when(searchResult.getResult()).thenReturn(List.of(refModel));

		dispatcher.dispatch(BillingPlatform.CHARGEBEE, raw);

		verify(refModel).setStatus(expectedStatus);
		verify(modelService).save(refModel);
	}

	private static NormalizedBillingEvent eventOf(final BillingEventType type, final String externalSubscriptionId)
	{
		return new NormalizedBillingEvent(BillingPlatform.CHARGEBEE, type, externalSubscriptionId, "cust-1",
				Instant.now(), Map.of());
	}
}
