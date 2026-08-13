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
package com.adyen.commerce.connector.chargebee.config.impl;

import org.apache.commons.lang3.StringUtils;

import com.adyen.commerce.connector.chargebee.config.ChargebeeConfigService;
import com.adyen.commerce.connector.exception.ConnectorNotConfiguredException;
import com.adyen.v6.model.ChargebeeConfigModel;

import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;

/**
 * Reads Chargebee configuration from the current {@link BaseStoreModel}'s {@code chargebeeConfig}
 * (Backoffice: Adyen Configuration &gt; Chargebee Config). The former {@code chargebee.*} properties
 * are gone: the store is the single source of truth so a multi-store setup can hold one Chargebee
 * site per base store.
 */
public class DefaultChargebeeConfigService implements ChargebeeConfigService
{
	private BaseStoreService baseStoreService;

	@Override
	public String getApiKey() throws ConnectorNotConfiguredException
	{
		return required(requireChargebeeConfig().getSubscriptionApiKey(), "subscriptionApiKey");
	}

	@Override
	public String getSiteName() throws ConnectorNotConfiguredException
	{
		return required(requireChargebeeConfig().getSubscriptionSiteId(), "subscriptionSiteId");
	}

	@Override
	public String getApiBaseUrl() throws ConnectorNotConfiguredException
	{
		return "https://" + getSiteName() + ".chargebee.com/api/v2";
	}

	@Override
	public String getGatewayAccountId()
	{
		final ChargebeeConfigModel config = findChargebeeConfig();
		return config == null ? null : StringUtils.trimToNull(config.getSubscriptionGatewayAccountId());
	}

	/**
	 * Read off the Chargebee configuration, not off the base store. The R2 guard compares this against
	 * the store's own Adyen merchant account, so taking it from the store would compare a value with
	 * itself and could never fail.
	 */
	@Override
	public String getConfiguredAdyenMerchantAccount()
	{
		final ChargebeeConfigModel config = findChargebeeConfig();
		return config == null ? null : StringUtils.trimToNull(config.getAdyenGatewayMerchantAccount());
	}

	@Override
	public String getWebhookUsername()
	{
		final ChargebeeConfigModel config = findChargebeeConfig();
		return config == null ? null : StringUtils.trimToNull(config.getChargebeeWebhookUsername());
	}

	@Override
	public String getWebhookPassword()
	{
		final ChargebeeConfigModel config = findChargebeeConfig();
		return config == null ? null : StringUtils.trimToNull(config.getChargebeeWebhookPassword());
	}

	/**
	 * The same lookup as {@link #requireChargebeeConfig()}, reported as {@code null} instead of thrown.
	 * Deliberately delegates rather than repeating the checks: the two must agree on exactly when a store
	 * counts as configured, and the callers are the getters the interface forbids from throwing.
	 */
	protected ChargebeeConfigModel findChargebeeConfig()
	{
		try
		{
			return requireChargebeeConfig();
		}
		catch (final ConnectorNotConfiguredException e)
		{
			return null;
		}
	}

	protected ChargebeeConfigModel requireChargebeeConfig() throws ConnectorNotConfiguredException
	{
		final BaseStoreModel baseStore = baseStoreService.getCurrentBaseStore();

		if (baseStore == null)
		{
			throw new ConnectorNotConfiguredException("No current base store");
		}

		final ChargebeeConfigModel config = baseStore.getChargebeeConfig();

		if (config == null)
		{
			throw new ConnectorNotConfiguredException(
					"Chargebee configuration is missing for base store '" + baseStore.getUid() + "'");
		}

		return config;
	}

	protected String required(final String value, final String attributeName) throws ConnectorNotConfiguredException
	{
		final String normalizedValue = StringUtils.trimToNull(value);

		if (normalizedValue == null)
		{
			throw new ConnectorNotConfiguredException("Missing Chargebee configuration attribute '" + attributeName + "'");
		}

		return normalizedValue;
	}

	public void setBaseStoreService(final BaseStoreService baseStoreService)
	{
		this.baseStoreService = baseStoreService;
	}
}
