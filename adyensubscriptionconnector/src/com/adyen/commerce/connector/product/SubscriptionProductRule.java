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
package com.adyen.commerce.connector.product;

import com.adyen.commerce.connector.exception.SubscriptionProductUndecidableException;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;

import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.store.BaseStoreModel;

/**
 * Decides what a subscription product is, for both the payment request (tokenize or not) and the activation
 * after payment, so the two always agree.
 */
public interface SubscriptionProductRule
{
	/**
	 * @param connector the store's active connector, which owns the product-to-plan mapping
	 * @param store     the store the product is sold in
	 * @param product   the product to classify
	 * @return whether the connector maps this product to a plan for this store
	 * @throws SubscriptionProductUndecidableException if the mapping could not be read, which callers must not
	 *         treat as "no"
	 */
	boolean isSubscriptionProduct(SubscriptionBillingConnector connector, BaseStoreModel store, ProductModel product)
			throws SubscriptionProductUndecidableException;
}
