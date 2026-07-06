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

import de.hybris.platform.servicelayer.config.ConfigurationService;

/**
 * Reads Chargebee configuration from the platform {@link ConfigurationService} (project/local.properties).
 */
public class DefaultChargebeeConfigService implements ChargebeeConfigService
{
	static final String P_SITE = "chargebee.site";
	static final String P_API_KEY = "chargebee.apiKey";
	static final String P_GATEWAY = "chargebee.gatewayAccountId";
	static final String P_MERCHANT = "chargebee.adyenMerchantAccount";

	private ConfigurationService configurationService;

	@Override
	public String getApiKey() throws ConnectorNotConfiguredException
	{
		return required(P_API_KEY);
	}

	@Override
	public String getSiteName() throws ConnectorNotConfiguredException
	{
		return required(P_SITE);
	}

	@Override
	public String getApiBaseUrl() throws ConnectorNotConfiguredException
	{
		return "https://" + getSiteName() + ".chargebee.com/api/v2";
	}

	@Override
	public String getGatewayAccountId()
	{
		return optional(P_GATEWAY);
	}

	@Override
	public String getConfiguredAdyenMerchantAccount()
	{
		return optional(P_MERCHANT);
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
		return StringUtils.trimToNull(configurationService.getConfiguration().getString(key, null));
	}

	public void setConfigurationService(final ConfigurationService configurationService)
	{
		this.configurationService = configurationService;
	}
}
