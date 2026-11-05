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
package com.adyen.commerce.connector.duplicate;

import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.store.BaseStoreModel;

/**
 * One question for {@link SubscriptionOwnershipService}: may this customer buy this product in this store?
 *
 * @param customer      who is buying
 * @param product       what they want to buy
 * @param baseStore     where; its {@code duplicateSubscriptionRule} and active billing platform decide
 * @param freshness     whether the billing platform may be asked
 * @param excludedOrder the cart or pending order the purchase belongs to, which must never block itself;
 *                      {@code null} when there is none
 * @param stage         where the check was asked from, for the log only; {@code null} reads as unknown
 */
public record DuplicateCheckRequest(CustomerModel customer, ProductModel product, BaseStoreModel baseStore,
		Freshness freshness, AbstractOrderModel excludedOrder, DuplicateCheckStage stage)
{
}
