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

/**
 * The shared rule, tested on its own rather than only through its two callers &mdash; because the whole
 * point of extracting it was that the two callers must not each hold an opinion about it.
 */
@UnitTest
public class DefaultSubscriptionProductRuleTest
{
	private static final String PRODUCT_CODE = "300938";

	private DefaultSubscriptionProductRule rule;
	private SubscriptionBillingConnector connector;

	@Before
	public void setUp() throws Exception
	{
		rule = new DefaultSubscriptionProductRule();
		connector = mock(SubscriptionBillingConnector.class);
		when(connector.platform()).thenReturn(BillingPlatform.RECURLY);
	}

	@Test
	public void aProductTheConnectorMapsToAPlanIsASubscriptionProduct() throws Exception
	{
		when(connector.resolvePlan(any(PlanResolutionRequest.class))).thenReturn(new PlanRef("plan-1", null));

		assertTrue(rule.isSubscriptionProduct(connector, product(PRODUCT_CODE)));
	}

	@Test
	public void probesByProductCode() throws Exception
	{
		when(connector.resolvePlan(any(PlanResolutionRequest.class))).thenReturn(new PlanRef("plan-1", null));

		rule.isSubscriptionProduct(connector, product(PRODUCT_CODE));

		final ArgumentCaptor<PlanResolutionRequest> request = ArgumentCaptor.forClass(PlanResolutionRequest.class);
		verify(connector).resolvePlan(request.capture());
		assertEquals(PRODUCT_CODE, request.getValue().productCode());
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

		assertFalse(rule.isSubscriptionProduct(connector, product(PRODUCT_CODE)));
	}

	@Test
	public void aResolverThatFailsIsNeitherAnswerAndSaysSo() throws Exception
	{
		final RetryableBillingException cause = new RetryableBillingException("Recurly timed out");
		when(connector.resolvePlan(any(PlanResolutionRequest.class))).thenThrow(cause);

		try
		{
			rule.isSubscriptionProduct(connector, product(PRODUCT_CODE));
			fail("Expected the rule to refuse to answer rather than guess");
		}
		catch (final SubscriptionProductUndecidableException e)
		{
			assertTrue(e.getMessage().contains(PRODUCT_CODE));
			assertSame(cause, e.getCause());
		}
	}

	/**
	 * FlexibleSearch throws unchecked, so without this the one case the callers most need to tell apart
	 * would be the one the compiler never makes them handle.
	 */
	@Test
	public void anUncheckedResolverFailureIsTranslatedTheSameWay() throws Exception
	{
		final IllegalStateException cause = new IllegalStateException("FlexibleSearch is unhappy");
		when(connector.resolvePlan(any(PlanResolutionRequest.class))).thenThrow(cause);

		try
		{
			rule.isSubscriptionProduct(connector, product(PRODUCT_CODE));
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
			rule.isSubscriptionProduct(connector, product(PRODUCT_CODE));
			fail("Expected the rule to refuse to answer");
		}
		catch (final SubscriptionProductUndecidableException e)
		{
			assertTrue(e.isRetryable());
		}
	}

	/**
	 * Nothing to ask about is not the same as being unable to ask: these are a plain no, not a refusal to
	 * answer, and no connector is troubled over them.
	 */
	@Test
	public void nothingToClassifyIsAPlainNo() throws Exception
	{
		assertFalse(rule.isSubscriptionProduct(connector, null));
		assertFalse(rule.isSubscriptionProduct(connector, product(null)));
		assertFalse(rule.isSubscriptionProduct(connector, product("   ")));
		assertFalse(rule.isSubscriptionProduct(null, product(PRODUCT_CODE)));

		verify(connector, never()).resolvePlan(any(PlanResolutionRequest.class));
	}

	private static ProductModel product(final String code)
	{
		final ProductModel product = mock(ProductModel.class);
		when(product.getCode()).thenReturn(code);
		return product;
	}
}
