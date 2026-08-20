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
 * Normalized inbound billing event types. Each connector maps its platform's webhook
 * vocabulary onto this enum in {@code parseWebhook}.
 *
 * <p>This is the union of the supported platforms' vocabularies rather than their intersection, plus
 * {@link #PAYMENT_METHOD_UPDATED}, which no parser emits yet and which is here for the connector that
 * will. Two values are reachable only from Chargebee. That asymmetry is deliberate: the platforms do not
 * cut the lifecycle in the same places — Recurly announces a new subscription as {@code created} and
 * reports a declined charge against the transaction, where Chargebee announces the same moment as
 * {@code subscription_activated} and reports the decline against the invoice — so a shared value would
 * have to claim an equivalence neither platform actually guarantees.</p>
 *
 * <p>Keeping both spellings costs little downstream, because no consumer reads a status off the type: the
 * dispatcher re-reads the platform for the authoritative state whatever arrives. It does, however, branch
 * on the type twice, and a new value has to be classified in both places on purpose. It treats
 * {@code UNKNOWN} as "not something we support" rather than "we could not find the subscription", and it
 * decides from {@code SUBSCRIPTION_SCOPED_TYPES} whether an event is about a subscription itself — which
 * is what makes it wait for a local reference to appear instead of skipping the delivery. Add a value
 * here and classify it there.</p>
 */
public enum BillingEventType
{
	SUBSCRIPTION_CREATED,
	/** Chargebee's {@code subscription_activated}. Recurly reports the same moment as {@link #SUBSCRIPTION_CREATED}. */
	SUBSCRIPTION_ACTIVATED,
	SUBSCRIPTION_UPDATED,
	SUBSCRIPTION_RENEWED,
	SUBSCRIPTION_CANCELLED,
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
