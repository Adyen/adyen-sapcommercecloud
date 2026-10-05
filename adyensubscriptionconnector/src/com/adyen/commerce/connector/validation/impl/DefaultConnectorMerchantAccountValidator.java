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
package com.adyen.commerce.connector.validation.impl;

import org.apache.commons.lang3.StringUtils;

import com.adyen.commerce.connector.exception.PreconditionFailedException;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;
import com.adyen.commerce.connector.validation.ConnectorMerchantAccountValidator;
import com.adyen.v6.strategy.AdyenMerchantAccountStrategy;

import de.hybris.platform.store.BaseStoreModel;

/**
 * Default validator. A blank {@code configuredAdyenMerchantAccount()} is rejected as "not configured", so an
 * incompletely configured gateway cannot disable the check by accident.
 */
public class DefaultConnectorMerchantAccountValidator implements ConnectorMerchantAccountValidator
{
	private AdyenMerchantAccountStrategy adyenMerchantAccountStrategy;

	@Override
	public void validate(final SubscriptionBillingConnector connector, final BaseStoreModel store)
			throws PreconditionFailedException
	{
		if (connector == null)
		{
			throw new PreconditionFailedException("No connector to validate");
		}

		final String connectorAccount = connector.configuredAdyenMerchantAccount();
		if (StringUtils.isBlank(connectorAccount))
		{
			throw new PreconditionFailedException(String.format(
					"Connector '%s' has no configured Adyen merchant account, so that guarantee cannot be "
							+ "established for base store '%s'. Set the platform's Adyen Gateway Merchant Account "
							+ "in its subscription configuration.",
					connector.platform(), store == null ? "<null>" : store.getUid()));
		}

		final String storeAccount = store == null ? null : adyenMerchantAccountStrategy.getWebMerchantAccount(store);

		if (!StringUtils.equals(connectorAccount, storeAccount))
		{
			throw new PreconditionFailedException(String.format(
					"Connector '%s' is configured for Adyen merchant account '%s' but base store '%s' uses '%s'. "
							+ "The Adyen token cannot be charged by a platform connected to a different merchant account.",
					connector.platform(), connectorAccount, store == null ? "<null>" : store.getUid(), storeAccount));
		}
	}

	public void setAdyenMerchantAccountStrategy(final AdyenMerchantAccountStrategy adyenMerchantAccountStrategy)
	{
		this.adyenMerchantAccountStrategy = adyenMerchantAccountStrategy;
	}
}
