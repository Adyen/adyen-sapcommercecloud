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
package com.adyen.commerce.connector.chargebee;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adyen.commerce.connector.chargebee.client.ChargebeeApiClient;
import com.adyen.commerce.connector.chargebee.client.ChargebeeSubscriptionParams;
import com.adyen.commerce.connector.chargebee.config.ChargebeeConfigService;
import com.adyen.commerce.connector.chargebee.plan.ChargebeePlanResolver;
import com.adyen.commerce.connector.dto.AdyenTokenHandle;
import com.adyen.commerce.connector.dto.BillingCustomerRef;
import com.adyen.commerce.connector.dto.BillingPaymentMethodRef;
import com.adyen.commerce.connector.dto.BillingSubscriptionRef;
import com.adyen.commerce.connector.dto.CancelReason;
import com.adyen.commerce.connector.dto.ConnectorCapabilities;
import com.adyen.commerce.connector.dto.CustomerSyncRequest;
import com.adyen.commerce.connector.dto.PlanRef;
import com.adyen.commerce.connector.dto.PlanResolutionRequest;
import com.adyen.commerce.connector.dto.RawWebhook;
import com.adyen.commerce.connector.dto.RecurringProcessingModel;
import com.adyen.commerce.connector.dto.SubscriptionCancelRequest;
import com.adyen.commerce.connector.dto.SubscriptionCreateRequest;
import com.adyen.commerce.connector.dto.SubscriptionPauseRequest;
import com.adyen.commerce.connector.dto.SubscriptionUpdateRequest;
import com.adyen.commerce.connector.dto.TokenImportRequest;
import com.adyen.commerce.connector.dto.TokenImportStyle;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.CapabilityUnsupportedException;
import com.adyen.commerce.connector.exception.PreconditionFailedException;
import com.adyen.commerce.connector.exception.TerminalBillingException;

import de.hybris.bootstrap.annotations.UnitTest;

/**
 * Unit test for {@link ChargebeeSubscriptionBillingConnector} against a mocked API client / config /
 * plan resolver (Phase 2, task P2.1&ndash;P2.3).
 */
@UnitTest
public class ChargebeeSubscriptionBillingConnectorTest
{
	@Mock
	private ChargebeeApiClient apiClient;
	@Mock
	private ChargebeeConfigService configService;
	@Mock
	private ChargebeePlanResolver planResolver;

	private ChargebeeSubscriptionBillingConnector connector;

	@Before
	public void setUp()
	{
		MockitoAnnotations.openMocks(this);
		connector = new ChargebeeSubscriptionBillingConnector();
		connector.setApiClient(apiClient);
		connector.setConfigService(configService);
		connector.setPlanResolver(planResolver);
	}

	@Test
	public void platformIsChargebee()
	{
		assertEquals(BillingPlatform.CHARGEBEE, connector.platform());
	}

	@Test
	public void capabilitiesReflectChargebee()
	{
		final ConnectorCapabilities caps = connector.capabilities();
		assertFalse(caps.requiresNetworkTransactionId());
		assertTrue(caps.supportsImmediateStart());
		assertFalse(caps.supportsPause());
		assertTrue(caps.requiresPreConfiguredPlan());
		assertTrue(caps.liveTokenValidationOnImport());
		assertEquals(TokenImportStyle.SLASH_JOINED, caps.tokenImportStyle());
	}

	@Test
	public void configuredMerchantAccountComesFromConfig()
	{
		when(configService.getConfiguredAdyenMerchantAccount()).thenReturn("MERCH");
		assertEquals("MERCH", connector.configuredAdyenMerchantAccount());
	}

	@Test
	public void ensureCustomerReturnsRef() throws Exception
	{
		when(apiClient.ensureCustomer("cust", "e@x.com", "First", "Last")).thenReturn("cb-cust-1");

		final BillingCustomerRef ref = connector
				.ensureCustomer(new CustomerSyncRequest("cust", "e@x.com", "First", "Last", Map.of()));

		assertEquals(BillingPlatform.CHARGEBEE, ref.platform());
		assertEquals("cb-cust-1", ref.externalId());
	}

	@Test
	public void importAdyenTokenBuildsSlashJoinedReferenceId() throws Exception
	{
		when(configService.getConfiguredAdyenMerchantAccount()).thenReturn("MERCH");
		when(apiClient.importPermanentToken(any(), any(), any())).thenReturn("pm-1");
		final AdyenTokenHandle token = new AdyenTokenHandle("MERCH", "shopper-1", "TOK-1", null, null);

		final BillingPaymentMethodRef ref = connector.importAdyenToken(new TokenImportRequest(
				new BillingCustomerRef(BillingPlatform.CHARGEBEE, "cb-cust-1"), token, RecurringProcessingModel.SUBSCRIPTION));

		assertEquals("pm-1", ref.externalId());
		verify(apiClient).importPermanentToken("cb-cust-1", "shopper-1/TOK-1", null);
	}

	@Test
	public void importAdyenTokenRejectsMerchantAccountMismatch() throws Exception
	{
		when(configService.getConfiguredAdyenMerchantAccount()).thenReturn("OTHER");
		final AdyenTokenHandle token = new AdyenTokenHandle("MERCH", "shopper-1", "TOK-1", null, null);

		assertThrows(PreconditionFailedException.class, () -> connector.importAdyenToken(new TokenImportRequest(
				new BillingCustomerRef(BillingPlatform.CHARGEBEE, "cb-cust-1"), token, RecurringProcessingModel.SUBSCRIPTION)));
		verifyNoTokenImport();
	}

	@Test
	public void resolvePlanDelegatesToResolver() throws Exception
	{
		final PlanRef plan = new PlanRef("price-1", null);
		when(planResolver.resolve(any())).thenReturn(plan);

		assertSame(plan, connector.resolvePlan(new PlanResolutionRequest("PROD-1", Map.of())));
	}

	@Test
	public void createSubscriptionMapsPlanAndIdempotencyKey() throws Exception
	{
		when(apiClient.createSubscription(any())).thenReturn("sub-1");
		final SubscriptionCreateRequest request = new SubscriptionCreateRequest(
				new BillingCustomerRef(BillingPlatform.CHARGEBEE, "cb-cust-1"),
				new BillingPaymentMethodRef(BillingPlatform.CHARGEBEE, "pm-1"), new PlanRef("price-1", null), 2, null, "EUR",
				null, null, Map.of("sapOrderCode", "ORDER-1"), "ORDER-1");

		final BillingSubscriptionRef ref = connector.createSubscription(request);

		assertEquals("sub-1", ref.externalId());
		final ArgumentCaptor<ChargebeeSubscriptionParams> captor = ArgumentCaptor.forClass(ChargebeeSubscriptionParams.class);
		verify(apiClient).createSubscription(captor.capture());
		final ChargebeeSubscriptionParams params = captor.getValue();
		assertEquals("cb-cust-1", params.customerId());
		assertEquals("price-1", params.itemPriceId());
		assertEquals(2, params.quantity());
		assertEquals("ORDER-1", params.subscriptionId());
	}

	@Test
	public void updateSubscriptionUsesPlanIdNotPriceId() throws Exception
	{
		// guards the itemPriceId fix: the sendable id is planId; priceId must be ignored
		connector.updateSubscription(new SubscriptionUpdateRequest(
				new BillingSubscriptionRef(BillingPlatform.CHARGEBEE, "sub-1"), new PlanRef("price-9", "IGNORED"), 3, null,
				Map.of(), "k"));

		verify(apiClient).updateSubscription("sub-1", "price-9", 3);
	}

	@Test
	public void updateSubscriptionWithoutPlanPassesNullItemPrice() throws Exception
	{
		connector.updateSubscription(new SubscriptionUpdateRequest(
				new BillingSubscriptionRef(BillingPlatform.CHARGEBEE, "sub-1"), null, 5, null, Map.of(), "k"));

		verify(apiClient).updateSubscription("sub-1", null, 5);
	}

	@Test
	public void cancelSubscriptionMapsAtPeriodEnd() throws Exception
	{
		connector.cancelSubscription(new SubscriptionCancelRequest(
				new BillingSubscriptionRef(BillingPlatform.CHARGEBEE, "sub-1"), CancelReason.REQUESTED_BY_CUSTOMER, true, "k"));

		verify(apiClient).cancelSubscription("sub-1", true);
	}

	@Test
	public void pauseIsUnsupportedByDefault()
	{
		assertThrows(CapabilityUnsupportedException.class, () -> connector.pauseSubscription(
				new SubscriptionPauseRequest(new BillingSubscriptionRef(BillingPlatform.CHARGEBEE, "sub-1"), null, "k")));
	}

	@Test
	public void parseWebhookNotYetImplemented()
	{
		assertThrows(TerminalBillingException.class, () -> connector.parseWebhook(new RawWebhook(Map.of(), "{}", null)));
	}

	private void verifyNoTokenImport() throws Exception
	{
		verify(apiClient, never()).importPermanentToken(any(), any(), any());
	}
}
