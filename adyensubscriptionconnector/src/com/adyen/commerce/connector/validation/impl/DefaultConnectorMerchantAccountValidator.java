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

import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.PreconditionFailedException;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;
import com.adyen.commerce.connector.validation.ConnectorMerchantAccountValidator;
import com.adyen.v6.strategy.AdyenMerchantAccountStrategy;

import de.hybris.platform.store.BaseStoreModel;

/**
 * Default validator. Only {@code ADYEN_NATIVE} is exempt, because it is the one path with no external
 * gateway to bind. A blank {@code configuredAdyenMerchantAccount()} from any other connector is read as
 * "not configured" and rejected, not as an exemption — the SPI notes that returning {@code null} disables
 * this check, and an incompletely configured gateway must not be able to disable it by accident.
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
			// Only the built-in Adyen-native path genuinely has no external gateway to bind, so only it is
			// exempt. For an external connector a blank answer means "not configured yet", and treating that
			// as an exemption would disable the check precisely while the operator is still setting the gateway up
			// — and it would do so silently, before activateSubscription creates the customer remotely.
			if (BillingPlatform.ADYEN_NATIVE.equals(connector.platform()))
			{
				return;
			}
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
