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
 */
public enum BillingEventType
{
	SUBSCRIPTION_CREATED,
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
	INVOICE_PAYMENT_FAILED,
	PAYMENT_SUCCEEDED,
	PAYMENT_FAILED,
	PAYMENT_METHOD_UPDATED,
	UNKNOWN
}
