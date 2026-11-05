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

import de.hybris.platform.commerceservices.order.CommerceCartCalculationStrategy;
import de.hybris.platform.commerceservices.order.CommerceCartModification;
import de.hybris.platform.commerceservices.order.CommerceCartModificationException;
import de.hybris.platform.commerceservices.order.hook.CommerceAddToCartMethodHook;
import de.hybris.platform.commerceservices.service.data.CommerceCartParameter;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.CartModel;

/**
 * Applies the subscription cart rules to an entry that was just added.
 *
 * <p>A hook rather than a validator or a strategy of its own. An {@code AddToCartValidator} can only refuse by
 * throwing, which the storefronts show as a generic error and OCC as a 400 with no status code. Replacing the
 * add-to-cart strategy would fight the other modules that alias it (selective cart, configurable bundle). The
 * after-hook gets the {@code CommerceCartModification} the platform is about to return, so the entry can be taken
 * back out (or cut to quantity one) and the modification rewritten with the status code and the
 * {@code quantityAdded} that really happened: the add-to-cart popup then shows {@code noItemsAdded.<code>} or
 * {@code reducedNumberOfItemsAdded.<code>} by itself.</p>
 *
 * <p>{@code addToCart(List)} with two different subscription products in one call: each is weighed in turn as the
 * platform adds it, so which of the two stays in the cart is unspecified (in practice the one added first); the
 * other gets {@code subscriptionLimitPerCart}.</p>
 */
public class SubscriptionAddToCartHook implements CommerceAddToCartMethodHook
{
	private SubscriptionCartGuard subscriptionCartGuard;
	private CommerceCartCalculationStrategy commerceCartCalculationStrategy;

	@Override
	public void beforeAddToCart(final CommerceCartParameter parameters) throws CommerceCartModificationException
	{
		// Nothing to prevent here: the entry has to exist before it can be weighed against the rest of the cart.
	}

	@Override
	public void afterAddToCart(final CommerceCartParameter parameters, final CommerceCartModification result)
			throws CommerceCartModificationException
	{
		final CartModel cart = parameters == null ? null : parameters.getCart();
		final AbstractOrderEntryModel entry = result == null ? null : result.getEntry();
		// Nothing was added (no stock, unavailable): the entry is a stand-in that belongs to no cart.
		if (cart == null || entry == null || entry.getOrder() == null || result.getQuantityAdded() <= 0)
		{
			return;
		}

		final Optional<CartAdjustment> adjustment = subscriptionCartGuard.checkAddedEntry(cart, entry);
		if (adjustment.isEmpty())
		{
			return;
		}
		apply(parameters, cart, result, adjustment.get());
	}

	protected void apply(final CommerceCartParameter parameters, final CartModel cart,
			final CommerceCartModification result, final CartAdjustment adjustment)
	{
		final AbstractOrderEntryModel entry = adjustment.entry();
		subscriptionCartGuard.apply(cart, adjustment);

		result.setStatusCode(adjustment.statusCode());
		result.setQuantityAdded(adjustment.keepQuantity());
		if (adjustment.removesEntry())
		{
			result.setEntry(subscriptionCartGuard.emptyEntryFor(entry.getProduct()));
		}
		// The list variant of addToCart has already calculated by now; the single one calculates again afterwards.
		commerceCartCalculationStrategy.calculateCart(parameters);
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
