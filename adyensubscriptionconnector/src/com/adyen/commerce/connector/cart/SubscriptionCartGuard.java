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
package com.adyen.commerce.connector.cart;

import java.util.List;
import java.util.Optional;

import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.CartEntryModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.product.ProductModel;

/**
 * The cart rules for subscription products, in one place so that adding, changing and validating a cart agree:
 * the customer must not own the subscription already, the cart holds one subscription product, and its quantity
 * is one. Nothing here talks to a billing platform; ownership is answered from local records.
 *
 * <p>A product that cannot be classified is treated as not being a subscription (the cart is a courtesy, the check
 * before payment is the one that must be right) and the degradation is logged.</p>
 */
public interface SubscriptionCartGuard
{
	/**
	 * For an entry that has just been added to the cart.
	 *
	 * @return what must change about it, or empty when it may stay as it is
	 */
	Optional<CartAdjustment> checkAddedEntry(CartModel cart, AbstractOrderEntryModel entry);

	/**
	 * For an entry whose quantity has just been changed. Only the limits apply: a subscription already in the cart
	 * is not asked again whether the customer owns it.
	 */
	Optional<CartAdjustment> checkUpdatedEntry(CartModel cart, AbstractOrderEntryModel entry);

	/**
	 * For the whole cart, in entry order: the first subscription product wins, the rest go.
	 *
	 * @return the adjustments to apply, in entry order
	 */
	List<CartAdjustment> checkCart(CartModel cart);

	/**
	 * For the check in front of the payment: the subscription entries of the cart and the per-cart limits they break,
	 * with no ownership question (the payment check asks that of the billing platform itself) and no change to the
	 * cart.
	 */
	CartAssessment assessForAuthorisation(CartModel cart);

	/** Removes the entry or cuts its quantity, refreshes the cart and logs the adjustment. */
	void apply(CartModel cart, CartAdjustment adjustment);

	/** An entry that is not in any cart, for a modification that reports a product that is no longer there. */
	CartEntryModel emptyEntryFor(ProductModel product);
}
