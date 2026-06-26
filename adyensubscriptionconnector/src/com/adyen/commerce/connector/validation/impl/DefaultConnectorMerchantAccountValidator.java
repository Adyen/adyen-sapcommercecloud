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
 * Default validator. Connectors that declare {@code configuredAdyenMerchantAccount() == null}
 * (e.g. the Adyen-native connector) are exempt, since they have no external gateway binding.
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
		if (connectorAccount == null)
		{
			// Not applicable (e.g. ADYEN_NATIVE) — nothing to enforce.
			return;
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
