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

/**
 * Single source of the subscription-product verdict, asked from both sides of the payment:
 * {@code SubscriptionPaymentRequestDecorator} while the Adyen {@code /payments} request is assembled, to
 * decide whether the payment must leave a reusable token behind, and
 * {@code DefaultSubscriptionOrderActivator} once the money has moved, to decide whether to activate
 * anything. The two must reach the same verdict on every product: disagreement either tokenizes a payment
 * nothing ever uses or charges a shopper for a subscription that can never be activated.
 *
 * <p>A resolver that fails rather than answers is not folded into the boolean. The two callers handle that
 * case differently, so it is handed back to them as a checked
 * {@link SubscriptionProductUndecidableException}.</p>
 */
public interface SubscriptionProductRule
{
	/**
	 * @param connector the store's active connector, which owns the product-to-plan mapping
	 * @param product   the product to classify
	 * @return whether the connector maps this product to a plan
	 * @throws SubscriptionProductUndecidableException if the resolver failed rather than answered
	 */
	boolean isSubscriptionProduct(SubscriptionBillingConnector connector, ProductModel product)
			throws SubscriptionProductUndecidableException;
}
