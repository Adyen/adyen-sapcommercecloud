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
package com.adyen.commerce.connector.hook.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adyen.commerce.connector.dto.PlanRef;
import com.adyen.commerce.connector.dto.PlanResolutionRequest;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.ConnectorNotConfiguredException;
import com.adyen.commerce.connector.exception.PlanNotMappedException;
import com.adyen.commerce.connector.exception.RetryableBillingException;
import com.adyen.commerce.connector.registry.SubscriptionBillingConnectorRegistry;
import com.adyen.commerce.connector.service.SubscriptionBillingService;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commerceservices.service.data.CommerceCheckoutParameter;
import de.hybris.platform.commerceservices.service.data.CommerceOrderResult;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.store.BaseStoreModel;

/**
 * Unit test for {@link DefaultSubscriptionActivationPlaceOrderMethodHook} — which orders activate a
 * subscription, which are left alone, and the guarantee that nothing ever escapes into the checkout.
 */
@UnitTest
public class DefaultSubscriptionActivationPlaceOrderMethodHookTest
{
	private static final String SUB_PRODUCT = "sub-product";
	private static final String PLAIN_PRODUCT = "plain-product";

	@Mock
	private SubscriptionBillingService subscriptionBillingService;
	@Mock
	private SubscriptionBillingConnectorRegistry connectorRegistry;
	@Mock
	private SubscriptionBillingConnector connector;
	@Mock
	private CommerceCheckoutParameter parameter;
	@Mock
	private CommerceOrderResult result;
	@Mock
	private OrderModel order;
	@Mock
	private BaseStoreModel store;

	private DefaultSubscriptionActivationPlaceOrderMethodHook hook;

	@Before
	public void setUp() throws Exception
	{
		MockitoAnnotations.openMocks(this);

		hook = new DefaultSubscriptionActivationPlaceOrderMethodHook();
		hook.setSubscriptionBillingService(subscriptionBillingService);
		hook.setConnectorRegistry(connectorRegistry);

		when(result.getOrder()).thenReturn(order);
		when(order.getCode()).thenReturn("order-1");
		when(order.getStore()).thenReturn(store);
		when(store.getUid()).thenReturn("electronics");
		when(store.getActiveBillingPlatform()).thenReturn(BillingPlatform.CHARGEBEE);
		when(connectorRegistry.getActiveConnector(store)).thenReturn(connector);
		when(connector.platform()).thenReturn(BillingPlatform.CHARGEBEE);

		// Only SUB_PRODUCT is mapped to a plan; anything else is an ordinary product.
		when(connector.resolvePlan(any(PlanResolutionRequest.class))).thenAnswer(invocation -> {
			final PlanResolutionRequest request = invocation.getArgument(0);
			if (SUB_PRODUCT.equals(request.productCode()))
			{
				return new PlanRef("plan-1", null);
			}
			throw new PlanNotMappedException("no mapping for " + request.productCode());
		});
	}

	@Test
	public void activatesForAnOrderCarryingASubscriptionProduct() throws Exception
	{
		givenEntries(product(SUB_PRODUCT));

		hook.afterPlaceOrder(parameter, result);

		verify(subscriptionBillingService).activateSubscription(order, entryProduct(SUB_PRODUCT));
	}

	@Test
	public void ignoresAnOrderOfOrdinaryProducts() throws Exception
	{
		givenEntries(product(PLAIN_PRODUCT), product("another-plain"));

		hook.afterPlaceOrder(parameter, result);

		verifyNoInteractions(subscriptionBillingService);
	}

	/**
	 * Activation is idempotent on (order, platform), so a loop would silently discard everything after the
	 * first entry. The hook activates one and says so rather than pretending it handled them all.
	 */
	@Test
	public void activatesOnlyOnceWhenAnOrderCarriesSeveralSubscriptionProducts() throws Exception
	{
		when(connector.resolvePlan(any(PlanResolutionRequest.class))).thenReturn(new PlanRef("plan-1", null));
		givenEntries(product("sub-a"), product("sub-b"));

		hook.afterPlaceOrder(parameter, result);

		verify(subscriptionBillingService, times(1)).activateSubscription(any(), any());
	}

	@Test
	public void countsARepeatedProductOnce() throws Exception
	{
		givenEntries(product(SUB_PRODUCT), product(SUB_PRODUCT));

		hook.afterPlaceOrder(parameter, result);

		verify(subscriptionBillingService, times(1)).activateSubscription(order, entryProduct(SUB_PRODUCT));
	}

	@Test
	public void doesNothingWhenTheStoreSellsNoSubscriptions() throws Exception
	{
		when(store.getActiveBillingPlatform()).thenReturn(null);
		givenEntries(product(SUB_PRODUCT));

		hook.afterPlaceOrder(parameter, result);

		verify(connectorRegistry, never()).getActiveConnector(any());
		verifyNoInteractions(subscriptionBillingService);
	}

	@Test
	public void doesNothingForAnOrderWithoutAStore() throws Exception
	{
		when(order.getStore()).thenReturn(null);
		givenEntries(product(SUB_PRODUCT));

		hook.afterPlaceOrder(parameter, result);

		verifyNoInteractions(subscriptionBillingService);
	}

	@Test
	public void doesNothingWhenThereIsNoOrder() throws Exception
	{
		when(result.getOrder()).thenReturn(null);

		hook.afterPlaceOrder(parameter, result);

		verifyNoInteractions(subscriptionBillingService);
	}

	/**
	 * afterPlaceOrder runs after submitOrder and outside the strategy's try/finally, so anything escaping
	 * here would surface as a checkout failure for an order that is already placed and paid.
	 */
	@Test
	public void swallowsAnActivationFailureSoTheCheckoutStillSucceeds() throws Exception
	{
		givenEntries(product(SUB_PRODUCT));
		doThrow(new RetryableBillingException("Chargebee is down")).when(subscriptionBillingService)
				.activateSubscription(any(), any());

		hook.afterPlaceOrder(parameter, result);
	}

	@Test
	public void swallowsAMissingConnectorSoTheCheckoutStillSucceeds() throws Exception
	{
		givenEntries(product(SUB_PRODUCT));
		when(connectorRegistry.getActiveConnector(store))
				.thenThrow(new ConnectorNotConfiguredException("no connector for CHARGEBEE"));

		hook.afterPlaceOrder(parameter, result);

		verifyNoInteractions(subscriptionBillingService);
	}

	@Test
	public void swallowsAnUnexpectedRuntimeFailure() throws Exception
	{
		givenEntries(product(SUB_PRODUCT));
		when(order.getEntries()).thenThrow(new IllegalStateException("boom"));

		hook.afterPlaceOrder(parameter, result);
	}

	/**
	 * A resolver that breaks is not the same as a product that is not a subscription, but neither may take
	 * the checkout down with it.
	 */
	@Test
	public void skipsAProductWhoseResolverFails() throws Exception
	{
		when(connector.resolvePlan(any(PlanResolutionRequest.class)))
				.thenThrow(new RetryableBillingException("resolver exploded"));
		givenEntries(product(SUB_PRODUCT));

		hook.afterPlaceOrder(parameter, result);

		verifyNoInteractions(subscriptionBillingService);
	}

	@Test
	public void skipsAnEntryWithoutAUsableProduct() throws Exception
	{
		final ProductModel blank = product("   ");
		final AbstractOrderEntryModel noProduct = mock(AbstractOrderEntryModel.class);
		final AbstractOrderEntryModel blankCode = mock(AbstractOrderEntryModel.class);
		when(blankCode.getProduct()).thenReturn(blank);
		when(order.getEntries()).thenReturn(List.of(noProduct, blankCode));

		hook.afterPlaceOrder(parameter, result);

		verifyNoInteractions(subscriptionBillingService);
	}

	@Test
	public void theOtherTwoHookMethodsDoNothing() throws Exception
	{
		hook.beforePlaceOrder(parameter);
		hook.beforeSubmitOrder(parameter, result);

		verify(connectorRegistry, never()).getActiveConnector(any());
		verifyNoInteractions(subscriptionBillingService);
	}

	@Test
	public void resolvesEachDistinctProductCodeOnlyOnce() throws Exception
	{
		givenEntries(product(SUB_PRODUCT), product(SUB_PRODUCT), product(PLAIN_PRODUCT));

		hook.afterPlaceOrder(parameter, result);

		verify(connector, times(2)).resolvePlan(any(PlanResolutionRequest.class));
	}

	/**
	 * Built eagerly rather than in a stream mapped inside {@code when(...)}: stubbing one mock while the
	 * stubbing of another is still open is what Mockito reports as unfinished stubbing.
	 */
	private void givenEntries(final ProductModel... products)
	{
		final List<AbstractOrderEntryModel> entries = new ArrayList<>();
		for (final ProductModel product : products)
		{
			final AbstractOrderEntryModel entry = mock(AbstractOrderEntryModel.class);
			when(entry.getProduct()).thenReturn(product);
			entries.add(entry);
		}
		when(order.getEntries()).thenReturn(entries);
	}

	private ProductModel entryProduct(final String code)
	{
		return order.getEntries().stream()
				.map(AbstractOrderEntryModel::getProduct)
				.filter(p -> code.equals(p.getCode()))
				.findFirst()
				.orElseThrow();
	}

	private static ProductModel product(final String code)
	{
		final ProductModel product = mock(ProductModel.class);
		when(product.getCode()).thenReturn(code);
		return product;
	}

	@Test
	public void neverTouchesTheServiceWhenNothingIsMapped() throws Exception
	{
		givenEntries(product(PLAIN_PRODUCT));

		hook.afterPlaceOrder(parameter, result);

		verify(subscriptionBillingService, never()).activateSubscription(any(), any());
	}
}
