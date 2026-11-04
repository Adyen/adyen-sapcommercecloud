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

import java.util.List;

import com.adyen.commerce.connector.cart.CartAdjustment;
import com.adyen.commerce.connector.cart.SubscriptionCartGuard;

import de.hybris.platform.commerceservices.order.CommerceCartModification;
import de.hybris.platform.commerceservices.service.data.CommerceCartParameter;
import de.hybris.platform.commerceservices.strategies.hooks.CartValidationHook;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.CartModel;

/**
 * Cleans a cart of what it must not hold when it is validated: a subscription the customer already owns, a second
 * subscription product, a quantity above one. Runs on the accelerator's cart page, at the start of checkout and at
 * each checkout step, and on OCC {@code POST /carts/{cartId}/validate}, because all of them go through
 * {@code CartFacade.validateCartData()}.
 *
 * <p>A hook after the platform's own validation rather than a strategy of its own: platform modules (vouchers,
 * bundles, product configuration) register theirs the same way, whereas the {@code cartValidationStrategy} alias is
 * already taken by others. The commerce cart service recalculates the cart after validating, so this does not.</p>
 */
public class SubscriptionCartValidationHook implements CartValidationHook
{
	private SubscriptionCartGuard subscriptionCartGuard;

	@Override
	public void beforeValidateCart(final CommerceCartParameter parameter, final List<CommerceCartModification> modifications)
	{
		// Nothing to do: the platform's own checks run first, and this one weighs what they left.
	}

	@Override
	public void afterValidateCart(final CommerceCartParameter parameter, final List<CommerceCartModification> modifications)
	{
		final CartModel cart = parameter == null ? null : parameter.getCart();
		if (cart == null)
		{
			return;
		}
		for (final CartAdjustment adjustment : subscriptionCartGuard.checkCart(cart))
		{
			// Looked up and built before the entry is changed: a removed entry is no longer there to compare.
			final int reported = indexOfReport(modifications, adjustment.entry());
			final CommerceCartModification modification = modificationFor(adjustment);
			subscriptionCartGuard.apply(cart, adjustment);
			if (reported >= 0)
			{
				// The platform has already reported the entry as fine; saying so next to "removed" would
				// contradict it, so the report is replaced.
				modifications.set(reported, modification);
			}
			else
			{
				modifications.add(modification);
			}
		}
	}

	/** Reported like the platform reports a removed or reduced entry: the old quantity, and what is left. */
	protected CommerceCartModification modificationFor(final CartAdjustment adjustment)
	{
		final AbstractOrderEntryModel entry = adjustment.entry();
		final CommerceCartModification modification = new CommerceCartModification();
		modification.setStatusCode(adjustment.statusCode());
		modification.setQuantity(entry.getQuantity() == null ? 1L : entry.getQuantity());
		modification.setQuantityAdded(adjustment.keepQuantity());
		modification.setEntry(adjustment.removesEntry() ? subscriptionCartGuard.emptyEntryFor(entry.getProduct()) : entry);
		return modification;
	}

	/** @return where the platform's own report about this entry sits in the list, or {@code -1} */
	protected int indexOfReport(final List<CommerceCartModification> modifications, final AbstractOrderEntryModel entry)
	{
		for (int i = 0; i < modifications.size(); i++)
		{
			final CommerceCartModification existing = modifications.get(i);
			if (existing != null && existing.getEntry() != null
					&& (existing.getEntry() == entry || existing.getEntry().equals(entry)))
			{
				return i;
			}
		}
		return -1;
	}

	public void setSubscriptionCartGuard(final SubscriptionCartGuard subscriptionCartGuard)
	{
		this.subscriptionCartGuard = subscriptionCartGuard;
	}
}
