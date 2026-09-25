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
package com.adyen.commerce.connector.product.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.adyen.commerce.connector.dto.PlanRef;
import com.adyen.commerce.connector.dto.PlanResolutionRequest;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.PlanNotMappedException;
import com.adyen.commerce.connector.exception.RetryableBillingException;
import com.adyen.commerce.connector.exception.SubscriptionProductUndecidableException;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.store.BaseStoreModel;

/**
 * Unit test for the shared subscription-product rule, exercised on its own rather than only through its two
 * callers, neither of which may hold an opinion of its own about it.
 */
@UnitTest
public class DefaultSubscriptionProductRuleTest
{
	private static final String PRODUCT_CODE = "300938";
	private static final String STORE_UID = "electronics";

	private DefaultSubscriptionProductRule rule;
	private SubscriptionBillingConnector connector;
	private BaseStoreModel store;

	@Before
	public void setUp()
	{
		rule = new DefaultSubscriptionProductRule();
		connector = mock(SubscriptionBillingConnector.class);
		when(connector.platform()).thenReturn(BillingPlatform.RECURLY);
		store = mock(BaseStoreModel.class);
		when(store.getUid()).thenReturn(STORE_UID);
	}

	@Test
	public void aProductTheConnectorMapsToAPlanIsASubscriptionProduct() throws Exception
	{
		when(connector.resolvePlan(any(PlanResolutionRequest.class))).thenReturn(new PlanRef("plan-1", null));

		assertTrue(rule.isSubscriptionProduct(connector, store, product(PRODUCT_CODE)));
	}

	@Test
	public void probesByProductCodeInTheGivenStore() throws Exception
	{
		when(connector.resolvePlan(any(PlanResolutionRequest.class))).thenReturn(new PlanRef("plan-1", null));

		rule.isSubscriptionProduct(connector, store, product(PRODUCT_CODE));

		final ArgumentCaptor<PlanResolutionRequest> request = ArgumentCaptor.forClass(PlanResolutionRequest.class);
		verify(connector).resolvePlan(request.capture());
		assertEquals(PRODUCT_CODE, request.getValue().productCode());
		assertEquals(STORE_UID, request.getValue().baseStoreUid());
	}

	/** Without a store the mapping cannot be chosen, which is a refusal to answer, not a "no". */
	@Test
	public void refusesToAnswerWithoutAStore() throws Exception
	{
		try
		{
			rule.isSubscriptionProduct(connector, null, product(PRODUCT_CODE));
			fail("Expected the rule to refuse to answer without a store");
		}
		catch (final SubscriptionProductUndecidableException e)
		{
			assertTrue(e.getMessage().contains(PRODUCT_CODE));
		}
		verify(connector, never()).resolvePlan(any(PlanResolutionRequest.class));
	}

	/**
	 * "No mapping exists" is an answer, and the answer is no. Both callers rely on this being a plain
	 * {@code false} rather than a failure: it is the ordinary case for every line item in the store.
	 */
	@Test
	public void anUnmappedProductIsSimplyNotOne() throws Exception
	{
		when(connector.resolvePlan(any(PlanResolutionRequest.class)))
				.thenThrow(new PlanNotMappedException("no mapping"));

		assertFalse(rule.isSubscriptionProduct(connector, store, product(PRODUCT_CODE)));
	}

	@Test
	public void aResolverThatFailsIsNeitherAnswerAndSaysSo() throws Exception
	{
		final RetryableBillingException cause = new RetryableBillingException("Recurly timed out");
		when(connector.resolvePlan(any(PlanResolutionRequest.class))).thenThrow(cause);

		try
		{
			rule.isSubscriptionProduct(connector, store, product(PRODUCT_CODE));
			fail("Expected the rule to refuse to answer rather than guess");
		}
		catch (final SubscriptionProductUndecidableException e)
		{
			assertTrue(e.getMessage().contains(PRODUCT_CODE));
			assertSame(cause, e.getCause());
		}
	}

	/**
	 * FlexibleSearch throws unchecked, so the case the callers most need to tell apart is the one the
	 * compiler never makes them handle.
	 */
	@Test
	public void anUncheckedResolverFailureIsTranslatedTheSameWay() throws Exception
	{
		final IllegalStateException cause = new IllegalStateException("FlexibleSearch is unhappy");
		when(connector.resolvePlan(any(PlanResolutionRequest.class))).thenThrow(cause);

		try
		{
			rule.isSubscriptionProduct(connector, store, product(PRODUCT_CODE));
			fail("Expected the rule to refuse to answer rather than let the unchecked failure escape as itself");
		}
		catch (final SubscriptionProductUndecidableException e)
		{
			assertSame(cause, e.getCause());
		}
	}

	/**
	 * Retryable on purpose: the shopper has already paid by the time the activator asks, and a bounded
	 * series of retries costs less than dead-lettering the order the moment a lookup hiccups.
	 */
	@Test
	public void theUndecidableOutcomeIsRetryable() throws Exception
	{
		when(connector.resolvePlan(any(PlanResolutionRequest.class)))
				.thenThrow(new IllegalStateException("FlexibleSearch is unhappy"));

		try
		{
			rule.isSubscriptionProduct(connector, store, product(PRODUCT_CODE));
			fail("Expected the rule to refuse to answer");
		}
		catch (final SubscriptionProductUndecidableException e)
		{
			assertTrue(e.isRetryable());
		}
	}

	/**
	 * Nothing to ask about is not the same as being unable to ask: these are a plain no rather than a
	 * refusal to answer, and no connector is asked about them.
	 */
	@Test
	public void nothingToClassifyIsAPlainNo() throws Exception
	{
		assertFalse(rule.isSubscriptionProduct(connector, store, null));
		assertFalse(rule.isSubscriptionProduct(connector, store, product(null)));
		assertFalse(rule.isSubscriptionProduct(connector, store, product("   ")));
		assertFalse(rule.isSubscriptionProduct(null, store, product(PRODUCT_CODE)));

		verify(connector, never()).resolvePlan(any(PlanResolutionRequest.class));
	}

	private static ProductModel product(final String code)
	{
		final ProductModel product = mock(ProductModel.class);
		when(product.getCode()).thenReturn(code);
		return product;
	}
}
