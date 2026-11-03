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
 * Normalized webhook event types: the union of the platforms' vocabularies. A new value must also be
 * classified in the dispatcher's {@code SUBSCRIPTION_SCOPED_TYPES}; its test fails until it is.
 */
public enum BillingEventType
{
	SUBSCRIPTION_CREATED,
	/** Chargebee's {@code subscription_activated}. Recurly reports the same moment as {@link #SUBSCRIPTION_CREATED}. */
	SUBSCRIPTION_ACTIVATED,
	SUBSCRIPTION_UPDATED,
	SUBSCRIPTION_RENEWED,
	SUBSCRIPTION_CANCELLED,
	/** Ends with the current period (Chargebee's {@code subscription_cancellation_scheduled}). */
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
	/** Chargebee's invoice-scoped {@code payment_failed}. */
	INVOICE_PAYMENT_FAILED,
	PAYMENT_SUCCEEDED,
	PAYMENT_FAILED,
	PAYMENT_METHOD_UPDATED,
	UNKNOWN
}
