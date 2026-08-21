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
package com.adyen.commerce.connector.chargebee.client;

import com.adyen.commerce.connector.dto.CardMetadata;
import com.adyen.commerce.connector.dto.NormalizedSubscription;
import com.adyen.commerce.connector.exception.BillingException;

/**
 * Semantic client over the Chargebee REST API. Owns the wire format (endpoints, param names,
 * form-encoding, Basic auth, JSON parsing) and maps every HTTP/transport failure onto the normalized
 * {@link BillingException} taxonomy (429/5xx/transport &rarr; retryable, 4xx &rarr; terminal). No
 * vendor type leaks out of this boundary.
 */
public interface ChargebeeApiClient
{
	/**
	 * Create-or-find a customer (idempotent): retrieve by id, create on 404.
	 *
	 * @return the Chargebee customer id
	 */
	String ensureCustomer(String customerId, String email, String firstName, String lastName) throws BillingException;

	/**
	 * Import an externally-vaulted Adyen token as a Chargebee payment source via
	 * {@code create_using_permanent_token}.
	 *
	 * @param customerId  the Chargebee customer id
	 * @param referenceId {@code shopperReference/recurringDetailReference} (slash-joined)
	 * @param cardMetadata optional non-PCI card hints; may be {@code null}
	 * @return the Chargebee payment source id
	 */
	String importPermanentToken(String customerId, String referenceId, CardMetadata cardMetadata) throws BillingException;

	/**
	 * Create a subscription for an item price.
	 *
	 * @return the Chargebee subscription id
	 */
	String createSubscription(ChargebeeSubscriptionParams params) throws BillingException;

	/**
	 * Read back the subscription Chargebee currently holds. Reconciliation treats this as the
	 * authoritative state, so the snapshot is built from the retrieve response alone — never from what a
	 * webhook happened to say.
	 *
	 * @return a vendor-neutral snapshot of the Chargebee subscription
	 */
	NormalizedSubscription fetchSubscription(String subscriptionId) throws BillingException;

	/**
	 * Update an existing subscription. Null arguments are omitted (left unchanged).
	 */
	void updateSubscription(String subscriptionId, String itemPriceId, Integer quantity) throws BillingException;

	/**
	 * Cancel a subscription immediately or at end of the current term.
	 */
	void cancelSubscription(String subscriptionId, boolean atPeriodEnd) throws BillingException;
}
