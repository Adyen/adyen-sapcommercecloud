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
 * <p>The two travel together because they answer different questions and neither can be derived from the
 * other. {@link CancelReason} is what goes in the record; {@link CancellationTiming} is what happens to
 * the shopper's remaining paid period. Deriving one from the other would mean a caller with a legitimate
 * combination — the customer asked, and we agreed to end it today — could only express it by writing down
 * a reason that is not true.</p>
 *
 * <p>A single carrier rather than two parameters, because the next thing to be said here is already
 * visible: both platforms accept a refund or credit instruction alongside the cancellation
 * ({@code refund} on Recurly, {@code credit_option} and {@code unbilled_charges_option} on Chargebee) and
 * neither adapter sends one today. When that has to be expressed, it is a component here rather than
 * another argument at every call site.</p>
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
	 * Stop renewing and keep serving to the end of the paid period. The timing a shopper-facing caller
	 * wants; also the only one that means the same thing on every platform.
	 */
	public static SubscriptionCancellation endOfPeriod(final CancelReason reason)
	{
		return new SubscriptionCancellation(reason, CancellationTiming.AT_PERIOD_END);
	}

	/**
	 * End it now, forfeiting the rest of the paid period. See {@link CancellationTiming#IMMEDIATELY} for
	 * why this is not simply the same request in a hurry.
	 */
	public static SubscriptionCancellation immediately(final CancelReason reason)
	{
		return new SubscriptionCancellation(reason, CancellationTiming.IMMEDIATELY);
	}
}
