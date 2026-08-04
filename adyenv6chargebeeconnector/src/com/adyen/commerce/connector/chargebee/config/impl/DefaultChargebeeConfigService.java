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

import de.hybris.platform.store.BaseStore;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.impl.DefaultBaseStoreService;
import org.apache.commons.lang3.StringUtils;

import com.adyen.commerce.connector.chargebee.config.ChargebeeConfigService;
import com.adyen.commerce.connector.exception.ConnectorNotConfiguredException;

import de.hybris.platform.servicelayer.config.ConfigurationService;

/**
 * Reads Chargebee configuration from the platform {@link ConfigurationService} (project/local.properties).
 */
public class DefaultChargebeeConfigService implements ChargebeeConfigService
{

	private DefaultBaseStoreService baseStoreService;

	 private final BaseStoreModel baseStore = baseStoreService.getCurrentBaseStore();
	 final String siteId = baseStore.getChargebeeSiteId();
	 final String apiKey = baseStore.getChargebeeAPIKey();
	 final String gatewayAccountId = baseStore.getChargebeeGatewayAccountId();
	 final String adyenMerchantAccount = baseStore.getAdyenMerchantAccount();

	@Override
	public String getApiKey() throws ConnectorNotConfiguredException
	{
		return required(apiKey);
	}

	@Override
	public String getSiteName() throws ConnectorNotConfiguredException
	{
		return required(siteId);
	}

	@Override
	public String getApiBaseUrl() throws ConnectorNotConfiguredException
	{
		return "https://" + getSiteName() + ".chargebee.com/api/v2";
	}

	@Override
	public String getGatewayAccountId()
	{
		return optional(gatewayAccountId);
	}

	@Override
	public String getConfiguredAdyenMerchantAccount()
	{
		return optional(adyenMerchantAccount);
	}

	protected String required(final String key) throws ConnectorNotConfiguredException
	{
		final String value = optional(key);
		if (value == null)
		{
			throw new ConnectorNotConfiguredException("Missing Chargebee configuration property '" + key + "'");
		}
		return value;
	}

	protected String optional(final String key)
	{
		return StringUtils.trimToEmpty(key);
	}

	public void setBaseStoreService(final DefaultBaseStoreService baseStoreService)
	{
		this.baseStoreService = baseStoreService;
	}
}
