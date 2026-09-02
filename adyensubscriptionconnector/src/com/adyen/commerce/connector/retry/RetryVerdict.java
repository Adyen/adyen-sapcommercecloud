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
package com.adyen.commerce.connector.retry;

import java.time.Instant;

/**
 * What to do with a failure: try again at {@code nextAttemptAt}, or stop and dead-letter it.
 *
 * @param retry         whether the operation is due another attempt
 * @param nextAttemptAt when that attempt may run at the earliest; {@code null} when {@code retry} is false
 * @param reason        why, in words an operator reading the record can use. Always populated — a decision
 *                      to give up is exactly the one that has to explain itself.
 */
public record RetryVerdict(boolean retry, Instant nextAttemptAt, String reason)
{
	public static RetryVerdict retryAt(final Instant nextAttemptAt, final String reason)
	{
		return new RetryVerdict(true, nextAttemptAt, reason);
	}

	public static RetryVerdict giveUp(final String reason)
	{
		return new RetryVerdict(false, null, reason);
	}
}
