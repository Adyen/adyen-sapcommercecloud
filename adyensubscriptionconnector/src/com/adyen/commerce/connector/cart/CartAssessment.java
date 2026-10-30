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

import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.store.BaseStoreModel;

/**
 * What the check before authorisation needs to know about a cart, without ownership: which entries are
 * subscriptions, and which per-cart limits they break. Nothing is changed in the cart.
 *
 * @param store               the store the cart is sold in; {@code null} when the cart holds nothing to check
 * @param subscriptionEntries the entries that are subscription products, in entry order
 * @param limitViolations     the entries that break a per-cart limit, with the status code each would be given
 *                            ({@link SubscriptionCartStatus#LIMIT_PER_CART}, {@link SubscriptionCartStatus#QUANTITY_LIMIT}),
 *                            in entry order
 */
public record CartAssessment(BaseStoreModel store, List<AbstractOrderEntryModel> subscriptionEntries,
		List<CartAdjustment> limitViolations)
{
	/** A cart with no subscription in it, or one that cannot be classified. */
	public static CartAssessment none()
	{
		return new CartAssessment(null, List.of(), List.of());
	}

	public boolean hasSubscription()
	{
		return !subscriptionEntries.isEmpty();
	}
}
