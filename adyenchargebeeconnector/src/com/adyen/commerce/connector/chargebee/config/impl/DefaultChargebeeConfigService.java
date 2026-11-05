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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.adyen.commerce.connector.chargebee.config.ChargebeeConfigService;
import com.adyen.commerce.connector.chargebee.model.ChargebeeConfigModel;
import com.adyen.commerce.connector.exception.ConnectorNotConfiguredException;

import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;

/**
 * Reads Chargebee credentials from the current {@link BaseStoreModel}'s {@code chargebeeConfig}
 * (Backoffice: Base Store &gt; Subscription Billing &gt; Chargebee Config), so a multi-store setup can hold one
 * Chargebee site per base store. Transport tuning is the exception and comes from
 * {@code project/local.properties} ({@code chargebee.http.*}): it describes this installation's tolerance
 * for a slow Chargebee rather than the shop's relationship with it.
 */
public class DefaultChargebeeConfigService implements ChargebeeConfigService
{
	static final String P_CONNECT_TIMEOUT_MILLIS = "chargebee.http.connectTimeoutMillis";
	static final String P_RESPONSE_TIMEOUT_MILLIS = "chargebee.http.responseTimeoutMillis";
	static final String P_CONNECTION_REQUEST_TIMEOUT_MILLIS = "chargebee.http.connectionRequestTimeoutMillis";
	static final String P_MAX_CONNECTIONS = "chargebee.http.maxConnections";
	/** For local emulators / testing only; blank means the production {@code https://<site>.chargebee.com/api/v2}. */
	static final String P_API_BASE_URL_OVERRIDE = "chargebee.api.baseUrlOverride";
	static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]");
	static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 5000;
	// Sized for the checkout: activation runs on the shopper's request thread and can spend this three
	// times over, once per sequential call.
	static final int DEFAULT_RESPONSE_TIMEOUT_MILLIS = 5000;
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
		final String override = StringUtils.trimToNull(
				configurationService.getConfiguration().getString(P_API_BASE_URL_OVERRIDE, null));

		if (override != null)
		{
			return validateBaseUrlOverride(StringUtils.removeEnd(override, "/"));
		}

		return "https://" + getSiteName() + ".chargebee.com/api/v2";
	}

	/**
	 * Accepts {@code http} or {@code https} only for a loopback host. The override exists for local emulators,
	 * so on a live installation it must not be able to redirect the API key to a foreign host, even over
	 * https. The value is not echoed in the message.
	 */
	protected String validateBaseUrlOverride(final String baseUrl) throws ConnectorNotConfiguredException
	{
		try
		{
			final URI uri = new URI(baseUrl);
			final String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
			final String host = uri.getHost();

			if (StringUtils.isBlank(host))
			{
				throw invalidBaseUrlOverride();
			}
			if (("https".equals(scheme) || "http".equals(scheme)) && isLoopbackHost(host))
			{
				return baseUrl;
			}
		}
		catch (final URISyntaxException e)
		{
			// falls through to the same message
		}

		throw invalidBaseUrlOverride();
	}

	protected boolean isLoopbackHost(final String host)
	{
		return LOOPBACK_HOSTS.contains(host.toLowerCase(Locale.ROOT));
	}

	protected ConnectorNotConfiguredException invalidBaseUrlOverride()
	{
		return new ConnectorNotConfiguredException("Invalid value for property '" + P_API_BASE_URL_OVERRIDE
				+ "': must be an http(s) URL on a loopback host (localhost, 127.0.0.1, ::1)");
	}

	@Override
	public String getGatewayAccountId()
	{
		final ChargebeeConfigModel config = findChargebeeConfig();
		return config == null ? null : StringUtils.trimToNull(config.getSubscriptionGatewayAccountId());
	}

	/**
	 * Read off the Chargebee configuration, not off the base store: the gateway-binding guard compares this
	 * against the store's own Adyen merchant account, so taking it from the store would compare a value
	 * with itself and could never fail.
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
	 * A non-positive override is a misconfiguration, not a request for "no limit": zero or a negative value
	 * would mean an unbounded wait, so it falls back to the default.
	 */
	protected int positiveInt(final String key, final int defaultValue)
	{
		final int value = configurationService.getConfiguration().getInt(key, defaultValue);
		return value > 0 ? value : defaultValue;
	}

	/**
	 * The same lookup as {@link #requireChargebeeConfig()}, reported as {@code null} instead of thrown, for
	 * the getters the interface forbids from throwing. It delegates so the two cannot disagree on when a
	 * store counts as configured.
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
