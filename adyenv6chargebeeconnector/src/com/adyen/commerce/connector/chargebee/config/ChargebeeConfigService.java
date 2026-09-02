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
package com.adyen.commerce.connector.chargebee.config;

import com.adyen.commerce.connector.exception.ConnectorNotConfiguredException;

/**
 * Access to the Chargebee connector configuration (site, api key, gateway account, Adyen merchant account).
 */
public interface ChargebeeConfigService
{
	/**
	 * @return the Chargebee full-access API key (HTTP Basic username)
	 * @throws ConnectorNotConfiguredException if unset
	 */
	String getApiKey() throws ConnectorNotConfiguredException;

	/**
	 * @return the Chargebee site subdomain
	 * @throws ConnectorNotConfiguredException if unset
	 */
	String getSiteName() throws ConnectorNotConfiguredException;

	/**
	 * @return {@code https://<site>.chargebee.com/api/v2}
	 * @throws ConnectorNotConfiguredException if the site is unset
	 */
	String getApiBaseUrl() throws ConnectorNotConfiguredException;

	/**
	 * @return the Adyen gateway account id configured in Chargebee, or {@code null} if unset
	 */
	String getGatewayAccountId();

	/**
	 * @return the Adyen merchant account the Chargebee gateway is bound to (for the gateway-binding check), or {@code null}
	 */
	String getConfiguredAdyenMerchantAccount();

	/**
	 * @return the Basic Auth username configured on the Chargebee-side webhook (Settings &gt; Webhooks &gt;
	 *         "protected by basic authentication"), or {@code null} if unset
	 */
	String getWebhookUsername();

	/**
	 * @return the Basic Auth password configured on the Chargebee-side webhook, or {@code null} if unset
	 */
	String getWebhookPassword();

	// --- Transport tuning ---
	//
	// Deliberately not on the base store: these describe this installation's tolerance for a slow
	// Chargebee, not the shop's relationship with it, so they stay in project/local.properties.

	/** Time to establish the TCP/TLS connection before failing. */
	int getConnectTimeoutMillis();

	/** Time to wait for Chargebee's response before failing. Without it a hung call blocks forever. */
	int getResponseTimeoutMillis();

	/** How long a caller may wait for a free pooled connection before failing. */
	int getConnectionRequestTimeoutMillis();

	/** Size of the connection pool, total and per route — every call goes to the one Chargebee host. */
	int getMaxConnections();
}
