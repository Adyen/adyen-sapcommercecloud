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
 * Request to cancel a subscription.
 *
 * <p>The timing is a required {@link CancellationTiming} rather than a flag: a boolean would have a silent
 * default of {@code false}, which on Recurly is a terminate, and the enum lets each adapter branch on it
 * exhaustively, which makes a third timing a compile error.</p>
 */
public record SubscriptionCancelRequest(BillingSubscriptionRef subscription,
                                        CancelReason reason,
                                        CancellationTiming timing,
                                        String idempotencyKey)
{
	public SubscriptionCancelRequest
	{
		Dtos.requireValue(subscription, "subscription");
		Dtos.requireValue(reason, "reason");
		Dtos.requireValue(timing, "timing");
	}
}
