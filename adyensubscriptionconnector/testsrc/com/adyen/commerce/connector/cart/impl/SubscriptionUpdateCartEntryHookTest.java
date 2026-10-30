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

/** Raising a subscription's quantity: the platform applies it, the hook cuts it back and says so. */
@UnitTest
public class SubscriptionUpdateCartEntryHookTest
{
	private SubscriptionUpdateCartEntryHook hook;
	private SubscriptionCartGuard guard;
	private CommerceCartCalculationStrategy calculation;
	private CartModel cart;
	private CartEntryModel entry;
	private CommerceCartParameter parameter;
	private CommerceCartModification result;

	@Before
	public void setUp()
	{
		hook = new SubscriptionUpdateCartEntryHook();
		guard = mock(SubscriptionCartGuard.class);
		calculation = mock(CommerceCartCalculationStrategy.class);
		hook.setSubscriptionCartGuard(guard);
		hook.setCommerceCartCalculationStrategy(calculation);

		cart = mock(CartModel.class);
		entry = mock(CartEntryModel.class);
		when(entry.getProduct()).thenReturn(mock(ProductModel.class));
		when(entry.getOrder()).thenReturn(cart);
		when(entry.getQuantity()).thenReturn(2L);
		when(guard.checkUpdatedEntry(any(), any())).thenReturn(Optional.empty());

		parameter = new CommerceCartParameter();
		parameter.setCart(cart);
		result = new CommerceCartModification();
		result.setStatusCode(CommerceCartModificationStatus.SUCCESS);
		result.setQuantity(2);
		// From 1 to 2.
		result.setQuantityAdded(1);
		result.setEntry(entry);
	}

	@Test
	public void aQuantityRaisedAboveOneIsCutBackAndReportedWithTheRealChange() throws Exception
	{
		final CartAdjustment adjustment = new CartAdjustment(entry, SubscriptionCartStatus.QUANTITY_LIMIT, 1,
				DuplicateCheckStage.UPDATE_QUANTITY);
		when(guard.checkUpdatedEntry(cart, entry)).thenReturn(Optional.of(adjustment));

		hook.afterUpdateCartEntry(parameter, result);

		verify(guard).apply(cart, adjustment);
		assertEquals("subscriptionQuantityLimit", result.getStatusCode());
		assertEquals(1, result.getQuantity());
		// The shopper went from 1 to 1: nothing was added.
		assertEquals(0, result.getQuantityAdded());
		assertSame(entry, result.getEntry());
		verify(calculation).calculateCart(parameter);
	}

	@Test
	public void anEntryTheLimitRemovesIsReportedAsGone() throws Exception
	{
		final CartEntryModel standIn = mock(CartEntryModel.class);
		when(guard.emptyEntryFor(entry.getProduct())).thenReturn(standIn);
		final CartAdjustment adjustment = new CartAdjustment(entry, SubscriptionCartStatus.QUANTITY_LIMIT, 0,
				DuplicateCheckStage.UPDATE_QUANTITY);
		when(guard.checkUpdatedEntry(cart, entry)).thenReturn(Optional.of(adjustment));

		hook.afterUpdateCartEntry(parameter, result);

		assertEquals(0, result.getQuantity());
		assertSame(standIn, result.getEntry());
	}

	/** What the platform returns for an entry the shopper removed: it has the order, the product and no quantity. */
	@Test
	public void theStandInForARemovedEntryIsNotWeighed() throws Exception
	{
		final CartEntryModel standIn = mock(CartEntryModel.class);
		when(standIn.getOrder()).thenReturn(cart);
		when(standIn.getProduct()).thenReturn(mock(ProductModel.class));
		when(standIn.getQuantity()).thenReturn(null);
		result.setEntry(standIn);
		result.setQuantity(0);
		result.setQuantityAdded(-1);

		hook.afterUpdateCartEntry(parameter, result);

		verifyNoInteractions(guard);
		verifyNoInteractions(calculation);
		assertEquals(0, result.getQuantity());
		assertSame(standIn, result.getEntry());
	}

	@Test
	public void aQuantityOfOneIsNotEvenWeighed() throws Exception
	{
		when(entry.getQuantity()).thenReturn(1L);

		hook.afterUpdateCartEntry(parameter, result);

		verifyNoInteractions(guard);
	}

	@Test
	public void anEntryTheLimitLeavesAloneKeepsItsModification() throws Exception
	{
		hook.afterUpdateCartEntry(parameter, result);

		assertEquals(CommerceCartModificationStatus.SUCCESS, result.getStatusCode());
		verify(guard, never()).apply(any(), any());
		verifyNoInteractions(calculation);
	}

	@Test
	public void anEntryThatWasRemovedByTheUpdateIsNotLookedAt() throws Exception
	{
		when(entry.getOrder()).thenReturn(null);

		hook.afterUpdateCartEntry(parameter, result);

		verifyNoInteractions(guard);
	}
}
