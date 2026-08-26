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
 * The one answer to "is this a subscription product", asked from both sides of the payment.
 *
 * <p>{@code SubscriptionPaymentRequestDecorator} asks it while the Adyen {@code /payments} request is
 * being assembled, to decide whether the payment must leave a reusable token behind;
 * {@code DefaultSubscriptionOrderActivator} asks it again after the money has moved, to decide whether
 * to activate anything. Those two have to reach the same verdict on every product: a decorator that
 * thought the cart was a subscription while the activator did not would tokenize a payment nothing ever
 * uses, and the opposite would charge a shopper for a subscription that can never be activated. Two
 * copies of the rule would agree only for as long as nobody touched either, so there is one, here.</p>
 *
 * <h3>Why the failure is not folded into the answer</h3>
 * <p>The two callers must and do handle a resolver that cannot answer differently &mdash; see
 * {@link SubscriptionProductUndecidableException} and each caller's own javadoc &mdash; so this rule
 * hands that case back to them instead of picking one of the two behaviours for both. Checked on
 * purpose: neither caller may reach it by accident.</p>
 */
public interface SubscriptionProductRule
{
	/**
	 * @param connector the store's active connector, which owns the product-to-plan mapping
	 * @param product   the product to classify
	 * @return whether the connector maps this product to a plan
	 * @throws SubscriptionProductUndecidableException if the resolver failed rather than answered, which
	 *         is neither a yes nor a no and must not be flattened into either by the rule
	 */
	boolean isSubscriptionProduct(SubscriptionBillingConnector connector, ProductModel product)
			throws SubscriptionProductUndecidableException;
}
