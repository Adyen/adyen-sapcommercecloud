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
package com.adyen.commerce.connector.facades.data;

/**
 * What came of a shopper asking to put one card behind every subscription they have on a platform.
 *
 * <p>Counts rather than a single verdict, because a fan-out can half succeed: each subscription is a
 * separate call to the platform, and one refusing says nothing about the others. The page tells the shopper
 * what actually moved instead of rounding a partial result up to success or down to failure.</p>
 *
 * @param result what to say overall; {@code CHANGED_ALL_SUBSCRIPTIONS} only when nothing was left behind
 * @param moved  how many subscriptions now bill to the chosen card, the one the shopper asked about included
 * @param failed how many could not be moved
 */
public record PaymentMethodChangeReport(PaymentMethodChangeResult result, int moved, int failed)
{
	/**
	 * The report for a change that was made, or refused, as one call. The subscription the shopper named is the
	 * one that moved, so a success counts it; an account-wide change by the platform moved at least that one,
	 * and how many more is not known here.
	 */
	public static PaymentMethodChangeReport of(final PaymentMethodChangeResult result)
	{
		final boolean changed = result == PaymentMethodChangeResult.CHANGED_THIS_SUBSCRIPTION
				|| result == PaymentMethodChangeResult.CHANGED_ALL_SUBSCRIPTIONS;
		return new PaymentMethodChangeReport(result, changed ? 1 : 0, 0);
	}

	public boolean isPartial()
	{
		return moved > 0 && failed > 0;
	}
}
