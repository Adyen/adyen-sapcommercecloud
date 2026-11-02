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
package com.adyen.commerce.connector.chargebee.http;

import com.adyen.commerce.connector.exception.RetryableBillingException;

/**
 * Thin transport over the Chargebee REST API. Transport-level failures (timeouts, connection
 * resets) surface as {@link RetryableBillingException}; HTTP status classification is the caller's job.
 */
public interface ChargebeeHttpClient
{
	/**
	 * @param idempotencyKey optional {@code chargebee-idempotency-key} value; a non-blank key makes a
	 *                       replay of the same mutating call return the original result instead of duplicating
	 */
	ChargebeeHttpResponse post(String url, String authorizationHeader, String formBody, String idempotencyKey)
			throws RetryableBillingException;

	ChargebeeHttpResponse get(String url, String authorizationHeader) throws RetryableBillingException;
}
