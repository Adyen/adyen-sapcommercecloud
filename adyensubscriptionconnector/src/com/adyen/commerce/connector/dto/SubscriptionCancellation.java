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
 * What a caller is asking for when it cancels: why, and when it takes effect.
 *
 * <p>The two travel together because neither can be derived from the other. {@link CancelReason} is what
 * goes in the record; {@link CancellationTiming} is what happens to the shopper's remaining paid period,
 * so a legitimate combination — the customer asked, and it ends today — stays expressible without writing
 * down a reason that is not true.</p>
 *
 * <p>Both platforms accept a refund or credit instruction alongside the cancellation ({@code refund} on
 * Recurly, {@code credit_option} and {@code unbilled_charges_option} on Chargebee); neither adapter sends
 * one.</p>
 *
 * @param reason why the subscription is ending, for the record and for platforms that store their own
 *               cancellation code
 * @param timing when it takes effect
 */
public record SubscriptionCancellation(CancelReason reason, CancellationTiming timing)
{
	public SubscriptionCancellation
	{
		Dtos.requireValue(reason, "reason");
		Dtos.requireValue(timing, "timing");
	}

	/**
	 * Stop renewing and keep serving to the end of the paid period. The only timing that means the same
	 * thing on every platform.
	 */
	public static SubscriptionCancellation endOfPeriod(final CancelReason reason)
	{
		return new SubscriptionCancellation(reason, CancellationTiming.AT_PERIOD_END);
	}

	/** End it now, forfeiting the rest of the paid period. See {@link CancellationTiming#IMMEDIATELY}. */
	public static SubscriptionCancellation immediately(final CancelReason reason)
	{
		return new SubscriptionCancellation(reason, CancellationTiming.IMMEDIATELY);
	}
}
