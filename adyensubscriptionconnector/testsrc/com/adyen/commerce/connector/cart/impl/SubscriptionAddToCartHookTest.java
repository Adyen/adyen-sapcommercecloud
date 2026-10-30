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
package com.adyen.commerce.connector.cart.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.Before;
import org.junit.Test;

import com.adyen.commerce.connector.cart.CartAdjustment;
import com.adyen.commerce.connector.cart.SubscriptionCartGuard;
import com.adyen.commerce.connector.cart.SubscriptionCartStatus;
import com.adyen.commerce.connector.duplicate.DuplicateCheckStage;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commerceservices.order.CommerceCartCalculationStrategy;
import de.hybris.platform.commerceservices.order.CommerceCartModification;
import de.hybris.platform.commerceservices.order.CommerceCartModificationStatus;
import de.hybris.platform.commerceservices.service.data.CommerceCartParameter;
import de.hybris.platform.core.model.order.CartEntryModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.product.ProductModel;

/**
 * What the add-to-cart hook does with the modification the platform is about to return: it rewrites the status
 * code and quantity added to what really happened, and leaves every other add alone.
 */
@UnitTest
public class SubscriptionAddToCartHookTest
{
	private SubscriptionAddToCartHook hook;
	private SubscriptionCartGuard guard;
	private CommerceCartCalculationStrategy calculation;
	private CartModel cart;
	private CartEntryModel entry;
	private CartEntryModel standIn;
	private CommerceCartParameter parameter;
	private CommerceCartModification result;

	@Before
	public void setUp()
	{
		hook = new SubscriptionAddToCartHook();
		guard = mock(SubscriptionCartGuard.class);
		calculation = mock(CommerceCartCalculationStrategy.class);
		hook.setSubscriptionCartGuard(guard);
		hook.setCommerceCartCalculationStrategy(calculation);

		cart = mock(CartModel.class);
		entry = mock(CartEntryModel.class);
		standIn = mock(CartEntryModel.class);
		final ProductModel product = mock(ProductModel.class);
		when(entry.getProduct()).thenReturn(product);
		when(entry.getOrder()).thenReturn(cart);
		when(guard.emptyEntryFor(product)).thenReturn(standIn);
		when(guard.checkAddedEntry(any(), any())).thenReturn(Optional.empty());

		parameter = new CommerceCartParameter();
		parameter.setCart(cart);
		result = new CommerceCartModification();
		result.setStatusCode(CommerceCartModificationStatus.SUCCESS);
		result.setQuantity(2);
		result.setQuantityAdded(2);
		result.setEntry(entry);
	}

	@Test
	public void anEntryTheCustomerMustNotHaveIsTakenOutAndReportedAsNothingAdded() throws Exception
	{
		final CartAdjustment adjustment = new CartAdjustment(entry, SubscriptionCartStatus.ALREADY_OWNED, 0,
				DuplicateCheckStage.ADD_TO_CART);
		when(guard.checkAddedEntry(cart, entry)).thenReturn(Optional.of(adjustment));

		hook.afterAddToCart(parameter, result);

		verify(guard).apply(cart, adjustment);
		assertEquals("subscriptionAlreadyOwned", result.getStatusCode());
		assertEquals(0, result.getQuantityAdded());
		assertSame(standIn, result.getEntry());
		verify(calculation).calculateCart(parameter);
	}

	@Test
	public void aQuantityCutToOneIsReportedAsWhatWasReallyAdded() throws Exception
	{
		final CartAdjustment adjustment = new CartAdjustment(entry, SubscriptionCartStatus.QUANTITY_LIMIT, 1,
				DuplicateCheckStage.ADD_TO_CART);
		when(guard.checkAddedEntry(cart, entry)).thenReturn(Optional.of(adjustment));

		hook.afterAddToCart(parameter, result);

		assertEquals("subscriptionQuantityLimit", result.getStatusCode());
		assertEquals(1, result.getQuantityAdded());
		assertSame(entry, result.getEntry());
		assertEquals(2, result.getQuantity());
		verify(calculation).calculateCart(parameter);
	}

	@Test
	public void anEntryThatMayStayLeavesTheModificationAsItWas() throws Exception
	{
		hook.afterAddToCart(parameter, result);

		assertEquals(CommerceCartModificationStatus.SUCCESS, result.getStatusCode());
		assertEquals(2, result.getQuantityAdded());
		assertSame(entry, result.getEntry());
		verify(guard, never()).apply(any(), any());
		verifyNoInteractions(calculation);
	}

	/** Nothing was added (no stock, unavailable): the platform's stand-in entry is in no cart. */
	@Test
	public void aModificationThatAddedNothingIsNotLookedAt() throws Exception
	{
		result.setQuantityAdded(0);

		hook.afterAddToCart(parameter, result);

		verifyNoInteractions(guard);
	}

	@Test
	public void aStandInEntryThatBelongsToNoCartIsNotLookedAt() throws Exception
	{
		when(entry.getOrder()).thenReturn(null);

		hook.afterAddToCart(parameter, result);

		verifyNoInteractions(guard);
	}
}
