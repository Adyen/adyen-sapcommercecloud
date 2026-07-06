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
	 * @return the Adyen merchant account the Chargebee gateway is bound to (for the R2 check), or {@code null}
	 */
	String getConfiguredAdyenMerchantAccount();
}
