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
package com.adyen.commerce.connector.product.impl;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.adyen.commerce.connector.dto.PlanResolutionRequest;
import com.adyen.commerce.connector.exception.BillingException;
import com.adyen.commerce.connector.exception.PlanNotMappedException;
import com.adyen.commerce.connector.exception.SubscriptionProductUndecidableException;
import com.adyen.commerce.connector.product.SubscriptionProductRule;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;

import de.hybris.platform.core.model.product.ProductModel;

/**
 * A product is a subscription product exactly when the active connector can resolve a plan for it.
 *
 * <p>Probing is safe and cheap: both shipped resolvers answer from a FlexibleSearch over their own
 * mapping table, with no remote call and no side effect. It does mean an unmapped product is
 * indistinguishable from a non-subscription one &mdash; the failure mode is "nothing happens", which is
 * visible and harmless, unlike the alternative below.</p>
 *
 * <p>The tempting shortcut &mdash; call {@code activateSubscription} for every entry and treat
 * {@link PlanNotMappedException} as "not a subscription" &mdash; is wrong. Inside the service, plan
 * resolution runs <em>after</em> {@code ensureCustomer} and {@code importAdyenToken}, so every ordinary
 * line item in the cart would leave a real customer and an imported payment token behind on the billing
 * platform before being rejected.</p>
 *
 * <p>{@code RuntimeException} is translated here rather than left to escape as itself, because the
 * resolvers are FlexibleSearch-backed and FlexibleSearch throws unchecked: without this the one case the
 * callers most need to tell apart would be the one case the compiler never makes them handle.</p>
 */
public class DefaultSubscriptionProductRule implements SubscriptionProductRule
{
	@Override
	public boolean isSubscriptionProduct(final SubscriptionBillingConnector connector, final ProductModel product)
			throws SubscriptionProductUndecidableException
	{
		// Nothing to ask about, and nothing to be undecided over: an entry with no product, or a product
		// with no code, cannot carry a plan mapping. Callers skip these before getting here; the guard is
		// so that the rule itself has one answer for every input rather than an NPE for some.
		if (connector == null || product == null || StringUtils.isBlank(product.getCode()))
		{
			return false;
		}
		try
		{
			connector.resolvePlan(new PlanResolutionRequest(product.getCode(), Map.of()));
			return true;
		}
		catch (final PlanNotMappedException e)
		{
			return false;
		}
		catch (final BillingException | RuntimeException e)
		{
			throw new SubscriptionProductUndecidableException("Cannot decide whether product '" + product.getCode()
					+ "' is a " + connector.platform() + " subscription product", e);
		}
	}
}
