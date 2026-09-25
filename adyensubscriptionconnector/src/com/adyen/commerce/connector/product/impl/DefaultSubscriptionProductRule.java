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
import de.hybris.platform.store.BaseStoreModel;

/**
 * A product is a subscription product when the connector resolves a plan for it. Resolution is a local
 * lookup, unlike {@code activateSubscription}, which creates the customer and imports the token first.
 */
public class DefaultSubscriptionProductRule implements SubscriptionProductRule
{
	@Override
	public boolean isSubscriptionProduct(final SubscriptionBillingConnector connector, final BaseStoreModel store,
			final ProductModel product) throws SubscriptionProductUndecidableException
	{
		if (connector == null || product == null || StringUtils.isBlank(product.getCode()))
		{
			return false;
		}
		if (store == null || StringUtils.isBlank(store.getUid()))
		{
			throw new SubscriptionProductUndecidableException("Cannot decide whether product '" + product.getCode()
					+ "' is a subscription product without the store it is sold in", null);
		}
		try
		{
			connector.resolvePlan(new PlanResolutionRequest(product.getCode(), store.getUid(), Map.of()));
			return true;
		}
		catch (final PlanNotMappedException e)
		{
			return false;
		}
		catch (final BillingException | RuntimeException e)
		{
			throw new SubscriptionProductUndecidableException("Cannot decide whether product '" + product.getCode()
					+ "' is a " + connector.platform() + " subscription product in store '" + store.getUid() + "'", e);
		}
	}
}
