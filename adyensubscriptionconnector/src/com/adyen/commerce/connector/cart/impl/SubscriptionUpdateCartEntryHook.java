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

import java.util.Optional;

import com.adyen.commerce.connector.cart.CartAdjustment;
import com.adyen.commerce.connector.cart.SubscriptionCartGuard;
import com.adyen.commerce.connector.cart.SubscriptionCartStatus;

import de.hybris.platform.commerceservices.order.CommerceCartCalculationStrategy;
import de.hybris.platform.commerceservices.order.CommerceCartModification;
import de.hybris.platform.commerceservices.order.hook.CommerceUpdateCartEntryHook;
import de.hybris.platform.commerceservices.service.data.CommerceCartParameter;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.CartModel;

/**
 * Cuts a subscription entry back to quantity one when its quantity is raised. Like the add-to-cart hook it works on
 * the modification the platform is about to return, so the shopper is told with the status code
 * {@value SubscriptionCartStatus#QUANTITY_LIMIT} and the quantity that is really in the cart.
 */
public class SubscriptionUpdateCartEntryHook implements CommerceUpdateCartEntryHook
{
	private SubscriptionCartGuard subscriptionCartGuard;
	private CommerceCartCalculationStrategy commerceCartCalculationStrategy;

	@Override
	public void beforeUpdateCartEntry(final CommerceCartParameter parameter)
	{
		// Nothing to prevent here: the platform's answer says what the entry ended up as.
	}

	@Override
	public void afterUpdateCartEntry(final CommerceCartParameter parameter, final CommerceCartModification result)
	{
		final CartModel cart = parameter == null ? null : parameter.getCart();
		final AbstractOrderEntryModel entry = result == null ? null : result.getEntry();
		// The stand-in the platform returns for a removed entry (DefaultCommerceUpdateCartEntryStrategy) has its
		// order and product set but a null quantity; with no quantity, or one of 1, there is nothing to cut.
		if (cart == null || entry == null || entry.getOrder() == null || entry.getQuantity() == null
				|| entry.getQuantity() <= 1)
		{
			return;
		}

		final Optional<CartAdjustment> adjustment = subscriptionCartGuard.checkUpdatedEntry(cart, entry);
		if (adjustment.isEmpty())
		{
			return;
		}
		apply(parameter, cart, result, adjustment.get());
	}

	protected void apply(final CommerceCartParameter parameter, final CartModel cart,
			final CommerceCartModification result, final CartAdjustment adjustment)
	{
		final AbstractOrderEntryModel entry = adjustment.entry();
		final long quantityBefore = entry.getQuantity();
		subscriptionCartGuard.apply(cart, adjustment);

		result.setStatusCode(adjustment.statusCode());
		result.setQuantity(adjustment.keepQuantity());
		// quantityAdded is the change the shopper got, which is now smaller than the platform reported.
		result.setQuantityAdded(result.getQuantityAdded() - (quantityBefore - adjustment.keepQuantity()));
		if (adjustment.removesEntry())
		{
			result.setEntry(subscriptionCartGuard.emptyEntryFor(entry.getProduct()));
		}
		commerceCartCalculationStrategy.calculateCart(parameter);
	}

	public void setSubscriptionCartGuard(final SubscriptionCartGuard subscriptionCartGuard)
	{
		this.subscriptionCartGuard = subscriptionCartGuard;
	}

	public void setCommerceCartCalculationStrategy(final CommerceCartCalculationStrategy commerceCartCalculationStrategy)
	{
		this.commerceCartCalculationStrategy = commerceCartCalculationStrategy;
	}
}
