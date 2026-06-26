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
package com.adyen.commerce.connector.service.impl;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adyen.commerce.connector.dto.AdyenTokenHandle;
import com.adyen.commerce.connector.dto.BillingCustomerRef;
import com.adyen.commerce.connector.dto.BillingPaymentMethodRef;
import com.adyen.commerce.connector.dto.BillingSubscriptionRef;
import com.adyen.commerce.connector.dto.ConnectorCapabilities;
import com.adyen.commerce.connector.dto.PlanRef;
import com.adyen.commerce.connector.dto.TokenImportStyle;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.event.SubscriptionActivatedEvent;
import com.adyen.commerce.connector.exception.PreconditionFailedException;
import com.adyen.commerce.connector.model.BillingCustomerRefModel;
import com.adyen.commerce.connector.model.BillingPaymentMethodRefModel;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;
import com.adyen.commerce.connector.registry.SubscriptionBillingConnectorRegistry;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;
import com.adyen.commerce.connector.token.AdyenTokenHandleFactory;
import com.adyen.commerce.connector.validation.ConnectorMerchantAccountValidator;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.model.c2l.CurrencyModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.payment.PaymentInfoModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.servicelayer.event.EventService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.search.SearchResult;
import de.hybris.platform.store.BaseStoreModel;

/**
 * Unit test for {@link DefaultSubscriptionBillingService} — verifies the orchestration flow against a
 * mock connector (design P1.5 acceptance criterion).
 */
@UnitTest
public class DefaultSubscriptionBillingServiceTest
{
	@Mock
	private SubscriptionBillingConnectorRegistry connectorRegistry;
	@Mock
	private ConnectorMerchantAccountValidator merchantAccountValidator;
	@Mock
	private AdyenTokenHandleFactory tokenHandleFactory;
	@Mock
	private ModelService modelService;
	@Mock
	private FlexibleSearchService flexibleSearchService;
	@Mock
	private EventService eventService;
	@Mock
	private SubscriptionBillingConnector connector;
	@Mock
	private AbstractOrderModel order;
	@Mock
	private BaseStoreModel store;
	@Mock
	private CustomerModel customer;
	@Mock
	private PaymentInfoModel paymentInfo;
	@Mock
	private ProductModel subProduct;
	@Mock
	private CurrencyModel currency;
	@Mock
	private SearchResult<BillingSubscriptionRefModel> searchResult;

	private DefaultSubscriptionBillingService service;

	@Before
	public void setUp() throws Exception
	{
		MockitoAnnotations.openMocks(this);

		service = new DefaultSubscriptionBillingService();
		service.setConnectorRegistry(connectorRegistry);
		service.setMerchantAccountValidator(merchantAccountValidator);
		service.setTokenHandleFactory(tokenHandleFactory);
		service.setModelService(modelService);
		service.setFlexibleSearchService(flexibleSearchService);
		service.setEventService(eventService);
		service.setClock(Clock.fixed(Instant.parse("2026-06-25T10:00:00Z"), ZoneOffset.UTC));

		when(order.getStore()).thenReturn(store);
		when(order.getUser()).thenReturn(customer);
		when(order.getPaymentInfo()).thenReturn(paymentInfo);
		when(order.getCode()).thenReturn("ORDER-1");
		when(order.getCurrency()).thenReturn(currency);
		when(currency.getIsocode()).thenReturn("EUR");
		when(customer.getCustomerID()).thenReturn("shopper-1");
		when(customer.getUid()).thenReturn("alice@example.com");
		when(customer.getName()).thenReturn("Alice");
		when(customer.getBillingCustomerRefs()).thenReturn(Collections.emptyList());
		when(paymentInfo.getBillingPaymentMethodRefs()).thenReturn(Collections.emptyList());
		when(subProduct.getCode()).thenReturn("SUB-PROD");

		when(connectorRegistry.getActiveConnector(store)).thenReturn(connector);
		when(connector.platform()).thenReturn(BillingPlatform.CHARGEBEE);
		when(connector.capabilities()).thenReturn(noNtidCaps());
		when(tokenHandleFactory.create(order))
				.thenReturn(new AdyenTokenHandle("MERCH", "shopper-1", "TOKEN-1", null, null));
		when(connector.ensureCustomer(any()))
				.thenReturn(new BillingCustomerRef(BillingPlatform.CHARGEBEE, "cust-ext"));
		when(connector.importAdyenToken(any()))
				.thenReturn(new BillingPaymentMethodRef(BillingPlatform.CHARGEBEE, "pm-ext"));
		when(connector.resolvePlan(any())).thenReturn(new PlanRef("plan-1", null));
		when(connector.createSubscription(any()))
				.thenReturn(new BillingSubscriptionRef(BillingPlatform.CHARGEBEE, "sub-ext"));

		when(flexibleSearchService.<BillingSubscriptionRefModel> search(any(FlexibleSearchQuery.class)))
				.thenReturn(searchResult);
		when(searchResult.getResult()).thenReturn(Collections.emptyList());

		when(modelService.create(BillingCustomerRefModel.class)).thenReturn(mock(BillingCustomerRefModel.class));
		when(modelService.create(BillingPaymentMethodRefModel.class)).thenReturn(mock(BillingPaymentMethodRefModel.class));
		when(modelService.create(BillingSubscriptionRefModel.class)).thenReturn(mock(BillingSubscriptionRefModel.class));
	}

	@Test
	public void shouldDriveEnsureCustomerThenImportTokenThenCreateSubscription() throws Exception
	{
		final BillingSubscriptionRefModel result = service.activateSubscription(order, subProduct);

		assertNotNull(result);
		verify(merchantAccountValidator).validate(connector, store);

		final InOrder ordered = inOrder(connector);
		ordered.verify(connector).ensureCustomer(any());
		ordered.verify(connector).importAdyenToken(any());
		ordered.verify(connector).resolvePlan(any());
		ordered.verify(connector).createSubscription(any());

		verify(result).setExternalSubscriptionId("sub-ext");
		verify(modelService).save(result);
		verify(eventService).publishEvent(any(SubscriptionActivatedEvent.class));
	}

	@Test
	public void shouldRejectWhenConnectorRequiresNtidButTokenHasNone() throws Exception
	{
		when(connector.capabilities()).thenReturn(requiresNtidCaps());

		assertThrows(PreconditionFailedException.class, () -> service.activateSubscription(order, subProduct));
		verify(connector, never()).createSubscription(any());
	}

	@Test
	public void shouldBeIdempotentWhenSubscriptionAlreadyExists() throws Exception
	{
		final BillingSubscriptionRefModel existing = mock(BillingSubscriptionRefModel.class);
		when(searchResult.getResult()).thenReturn(List.of(existing));

		final BillingSubscriptionRefModel result = service.activateSubscription(order, subProduct);

		assertSame(existing, result);
		verify(connector, never()).ensureCustomer(any());
		verify(connector, never()).createSubscription(any());
	}

	private static ConnectorCapabilities noNtidCaps()
	{
		return new ConnectorCapabilities(false, true, false, true, true, TokenImportStyle.SLASH_JOINED);
	}

	private static ConnectorCapabilities requiresNtidCaps()
	{
		return new ConnectorCapabilities(true, false, false, true, false, TokenImportStyle.SEPARATE_FIELDS);
	}
}
