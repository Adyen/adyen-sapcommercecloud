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

import java.util.List;
import java.util.function.BooleanSupplier;

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
	 * @param customerId     the Chargebee customer id
	 * @param referenceId    {@code shopperReference/recurringDetailReference} (slash-joined)
	 * @param cardMetadata   optional non-PCI card hints; may be {@code null}
	 * @param idempotencyKey optional idempotency key for this call; blank sends none
	 * @return the Chargebee payment source id
	 */
	String importPermanentToken(String customerId, String referenceId, CardMetadata cardMetadata,
			String idempotencyKey) throws BillingException;

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
	 * Every subscription of the customer in any state, across pages ({@code customer_id[is]} filter). Not tied
	 * to what this integration created: the customer is the ownership boundary.
	 */
	default List<NormalizedSubscription> listCustomerSubscriptions(String customerId) throws BillingException
	{
		return listCustomerSubscriptions(customerId, () -> true);
	}

	/**
	 * As {@link #listCustomerSubscriptions(String)}, for a caller with a deadline: {@code mayContinue} is asked
	 * before each page after the first, and once it answers {@code false} the pages read so far are returned. A
	 * request already in flight is not interrupted; it is bounded by the transport timeout.
	 */
	List<NormalizedSubscription> listCustomerSubscriptions(String customerId, BooleanSupplier mayContinue)
			throws BillingException;

	/**
	 * Update an existing subscription. Null arguments are omitted (left unchanged).
	 *
	 * @param idempotencyKey optional idempotency key for this call; blank sends none
	 */
	void updateSubscription(String subscriptionId, String itemPriceId, Integer quantity, String idempotencyKey)
			throws BillingException;

	/**
	 * Cancel a subscription immediately or at end of the current term.
	 *
	 * @param idempotencyKey optional idempotency key for this call; blank sends none
	 */
	void cancelSubscription(String subscriptionId, boolean atPeriodEnd, String idempotencyKey) throws BillingException;
}
