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
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.adyen.commerce.connector.cart.CartAdjustment;
import com.adyen.commerce.connector.cart.SubscriptionCartGuard;
import com.adyen.commerce.connector.cart.SubscriptionCartStatus;
import com.adyen.commerce.connector.duplicate.DuplicateCheckStage;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commerceservices.order.CommerceCartModification;
import de.hybris.platform.commerceservices.order.CommerceCartModificationStatus;
import de.hybris.platform.commerceservices.service.data.CommerceCartParameter;
import de.hybris.platform.core.model.order.CartEntryModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.product.ProductModel;

/**
 * Validation: the hook reports what it removed or cut the way the platform reports its own removals, and puts its
 * report where the platform's "fine" for that entry was, so the cart page does not say both.
 */
@UnitTest
public class SubscriptionCartValidationHookTest
{
	private SubscriptionCartValidationHook hook;
	private SubscriptionCartGuard guard;
	private CartModel cart;
	private CommerceCartParameter parameter;
	private final List<CommerceCartModification> modifications = new ArrayList<>();

	@Before
	public void setUp()
	{
		hook = new SubscriptionCartValidationHook();
		guard = mock(SubscriptionCartGuard.class);
		hook.setSubscriptionCartGuard(guard);
		cart = mock(CartModel.class);
		parameter = new CommerceCartParameter();
		parameter.setCart(cart);
		when(guard.checkCart(cart)).thenReturn(List.of());
	}

	@Test
	public void aRemovedEntryReplacesThePlatformsReportThatItWasFine() throws Exception
	{
		final CartEntryModel owned = entry(3L);
		final CartEntryModel mug = entry(1L);
		final CartEntryModel standIn = mock(CartEntryModel.class);
		when(guard.emptyEntryFor(owned.getProduct())).thenReturn(standIn);
		modifications.add(success(owned));
		modifications.add(success(mug));
		when(guard.checkCart(cart)).thenReturn(List.of(new CartAdjustment(owned,
				SubscriptionCartStatus.ALREADY_OWNED, 0, DuplicateCheckStage.CART_VALIDATION)));

		hook.afterValidateCart(parameter, modifications);

		assertEquals(2, modifications.size());
		final CommerceCartModification reported = modifications.get(0);
		assertEquals("subscriptionAlreadyOwned", reported.getStatusCode());
		assertEquals(0, reported.getQuantityAdded());
		// The old quantity, as the platform reports a removal.
		assertEquals(3, reported.getQuantity());
		assertSame(standIn, reported.getEntry());
		assertEquals(CommerceCartModificationStatus.SUCCESS, modifications.get(1).getStatusCode());
		verify(guard).apply(any(), any());
	}

	@Test
	public void aReducedEntryIsReportedWithItsOldAndItsNewQuantity() throws Exception
	{
		final CartEntryModel big = entry(4L);
		modifications.add(success(big));
		when(guard.checkCart(cart)).thenReturn(List.of(new CartAdjustment(big,
				SubscriptionCartStatus.QUANTITY_LIMIT, 1, DuplicateCheckStage.CART_VALIDATION)));

		hook.afterValidateCart(parameter, modifications);

		final CommerceCartModification reported = modifications.get(0);
		assertEquals("subscriptionQuantityLimit", reported.getStatusCode());
		assertEquals(4, reported.getQuantity());
		assertEquals(1, reported.getQuantityAdded());
		assertSame(big, reported.getEntry());
	}

	@Test
	public void anAdjustmentWithNoReportOfTheEntryIsAppended() throws Exception
	{
		final CartEntryModel second = entry(1L);
		when(guard.emptyEntryFor(second.getProduct())).thenReturn(mock(CartEntryModel.class));
		when(guard.checkCart(cart)).thenReturn(List.of(new CartAdjustment(second,
				SubscriptionCartStatus.LIMIT_PER_CART, 0, DuplicateCheckStage.CART_VALIDATION)));

		hook.afterValidateCart(parameter, modifications);

		assertEquals(1, modifications.size());
		assertEquals("subscriptionLimitPerCart", modifications.get(0).getStatusCode());
	}

	@Test
	public void aCartThatNeedsNothingIsLeftAsTheValidationFoundIt() throws Exception
	{
		final CartEntryModel fine = entry(1L);
		modifications.add(success(fine));

		hook.afterValidateCart(parameter, modifications);

		assertEquals(1, modifications.size());
		assertEquals(CommerceCartModificationStatus.SUCCESS, modifications.get(0).getStatusCode());
		verify(guard, never()).apply(any(), any());
	}

	private CartEntryModel entry(final Long quantity)
	{
		final CartEntryModel entry = mock(CartEntryModel.class);
		when(entry.getProduct()).thenReturn(mock(ProductModel.class));
		when(entry.getQuantity()).thenReturn(quantity);
		return entry;
	}

	private CommerceCartModification success(final CartEntryModel entry)
	{
		final CommerceCartModification modification = new CommerceCartModification();
		modification.setStatusCode(CommerceCartModificationStatus.SUCCESS);
		modification.setEntry(entry);
		return modification;
	}
}
