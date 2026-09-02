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
 * <p>The timing is a {@link CancellationTiming} rather than a flag, and it is required. A boolean would
 * have a silent default here — {@code false}, which on Recurly is a terminate — so a caller that had not
 * thought about the question would have been answered with the destructive one. It also lets each adapter
 * branch on it exhaustively, which is what makes a third timing a compile error instead of whichever
 * behaviour the {@code else} happened to hold.</p>
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
