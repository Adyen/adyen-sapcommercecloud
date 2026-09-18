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
package com.adyen.commerce.connector.dto;

/**
 * Normalized inbound billing event types. Each connector maps its platform's webhook vocabulary onto this
 * enum in {@code parseWebhook}.
 *
 * <p>This is the union of the supported platforms' vocabularies rather than their intersection, because the
 * platforms do not cut the lifecycle in the same places: several values are reachable only from Chargebee,
 * and {@link #PAYMENT_METHOD_UPDATED} from no parser at all. No consumer reads a status off the type — the
 * dispatcher re-reads the platform for the authoritative state — but the dispatcher does classify the type
 * twice: as supported or {@code UNKNOWN}, and as subscription-scoped or not via
 * {@code SUBSCRIPTION_SCOPED_TYPES}, which is what makes it wait for a local reference to appear instead of
 * skipping the delivery. A new value has to be put on one of those lists; the dispatcher's test enumerates
 * this enum and fails until it is.</p>
 */
public enum BillingEventType
{
	SUBSCRIPTION_CREATED,
	/** Chargebee's {@code subscription_activated}. Recurly reports the same moment as {@link #SUBSCRIPTION_CREATED}. */
	SUBSCRIPTION_ACTIVATED,
	SUBSCRIPTION_UPDATED,
	SUBSCRIPTION_RENEWED,
	SUBSCRIPTION_CANCELLED,
	/**
	 * The subscription will end when the current period does, and is still serving until then. Chargebee's
	 * {@code subscription_cancellation_scheduled}, which is what its hosted portal's cancel button produces.
	 * Distinct from {@link #SUBSCRIPTION_CHANGE_SCHEDULED}, which means a scheduled change of plan.
	 */
	SUBSCRIPTION_CANCELLATION_SCHEDULED,
	/** A scheduled cancellation was called off and the subscription will renew after all. */
	SUBSCRIPTION_CANCELLATION_REMOVED,
	SUBSCRIPTION_EXPIRED,
	SUBSCRIPTION_PAUSED,
	SUBSCRIPTION_RESUMED,
	SUBSCRIPTION_CHANGE_SCHEDULED,
	SUBSCRIPTION_PAUSE_SCHEDULED,
	SUBSCRIPTION_PAUSE_UPDATED,
	SUBSCRIPTION_PAUSE_CANCELLED,
	INVOICE_PAID,
	INVOICE_PAST_DUE,
	INVOICE_FAILED,
	/**
	 * Chargebee's invoice-scoped {@code payment_failed}. Recurly draws the same line twice instead, as
	 * {@link #INVOICE_FAILED} for the invoice and {@link #PAYMENT_FAILED} for the transaction.
	 */
	INVOICE_PAYMENT_FAILED,
	PAYMENT_SUCCEEDED,
	PAYMENT_FAILED,
	PAYMENT_METHOD_UPDATED,
	UNKNOWN
}
