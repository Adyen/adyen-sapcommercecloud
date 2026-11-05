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

/**
 * The {@code CommerceCartModification} status codes this extension adds. Storefronts turn them into messages by
 * their code ({@code basket.validation.<code>}, {@code basket.information.quantity.noItemsAdded.<code>}), so the
 * values are a contract with the localization files and the Spartacus translations.
 */
public final class SubscriptionCartStatus
{
	/** The customer already owns this subscription; the entry is not added, or is removed. */
	public static final String ALREADY_OWNED = "subscriptionAlreadyOwned";

	/** The cart holds a different subscription product already; one subscription product per cart. */
	public static final String LIMIT_PER_CART = "subscriptionLimitPerCart";

	/** A subscription is bought once per order: the quantity was cut to one. */
	public static final String QUANTITY_LIMIT = "subscriptionQuantityLimit";

	private SubscriptionCartStatus()
	{
	}
}
