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

import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;

/**
 * Reads Chargebee configuration from the current {@link BaseStoreModel}'s {@code chargebeeConfig}
 * (Backoffice: Adyen Configuration &gt; Chargebee Config). The former {@code chargebee.*} credential
 * properties are gone: the store is the single source of truth so a multi-store setup can hold one
 * Chargebee site per base store.
 *
 * <p>Transport tuning is the exception and stays in {@code project/local.properties}
 * ({@code chargebee.http.*}): it describes this installation's tolerance for a slow Chargebee, not
 * the shop's relationship with it, and mirrors what the Recurly connector does.</p>
 */
public class DefaultChargebeeConfigService implements ChargebeeConfigService
{
	static final String P_CONNECT_TIMEOUT_MILLIS = "chargebee.http.connectTimeoutMillis";
	static final String P_RESPONSE_TIMEOUT_MILLIS = "chargebee.http.responseTimeoutMillis";
	static final String P_CONNECTION_REQUEST_TIMEOUT_MILLIS = "chargebee.http.connectionRequestTimeoutMillis";
	static final String P_MAX_CONNECTIONS = "chargebee.http.maxConnections";
	static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 5000;
	static final int DEFAULT_RESPONSE_TIMEOUT_MILLIS = 30000;
	static final int DEFAULT_CONNECTION_REQUEST_TIMEOUT_MILLIS = 5000;
	static final int DEFAULT_MAX_CONNECTIONS = 20;

	private BaseStoreService baseStoreService;
	private ConfigurationService configurationService;

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
	 * Read off the Chargebee configuration, not off the base store. The gateway-binding guard compares this against
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

	@Override
	public int getConnectTimeoutMillis()
	{
		return positiveInt(P_CONNECT_TIMEOUT_MILLIS, DEFAULT_CONNECT_TIMEOUT_MILLIS);
	}

	@Override
	public int getResponseTimeoutMillis()
	{
		return positiveInt(P_RESPONSE_TIMEOUT_MILLIS, DEFAULT_RESPONSE_TIMEOUT_MILLIS);
	}

	@Override
	public int getConnectionRequestTimeoutMillis()
	{
		return positiveInt(P_CONNECTION_REQUEST_TIMEOUT_MILLIS, DEFAULT_CONNECTION_REQUEST_TIMEOUT_MILLIS);
	}

	@Override
	public int getMaxConnections()
	{
		return positiveInt(P_MAX_CONNECTIONS, DEFAULT_MAX_CONNECTIONS);
	}

	/**
	 * A non-positive override is a misconfiguration, not a request for "no limit": zero or a negative
	 * value would restore the unbounded wait this configuration exists to remove, so it falls back.
	 */
	protected int positiveInt(final String key, final int defaultValue)
	{
		final int value = configurationService.getConfiguration().getInt(key, defaultValue);
		return value > 0 ? value : defaultValue;
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

	public void setConfigurationService(final ConfigurationService configurationService)
	{
		this.configurationService = configurationService;
	}
}
