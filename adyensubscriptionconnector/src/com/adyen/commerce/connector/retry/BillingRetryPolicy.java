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

import com.adyen.commerce.connector.exception.BillingException;
import com.adyen.commerce.connector.exception.RetryableBillingException;

/**
 * The single place that answers "should this be tried again, and when?".
 *
 * <p>Both failure paths ask it — an activation the core drives itself, and an inbound webhook whose retries
 * belong to the platform — so the two cannot disagree on what is worth retrying; only the activation path
 * acts on {@link RetryVerdict#nextAttemptAt()}, because only it owns the retry. The classification is the
 * one {@link BillingException#isRetryable()} describes: {@link RetryableBillingException} may be tried
 * again, every other {@code BillingException} will fail the same way on replay and is given up on at
 * once.</p>
 */
public interface BillingRetryPolicy
{
	/**
	 * @param failure      what went wrong
	 * @param attemptsSoFar how many attempts have run <em>including</em> the one that just failed, so the
	 *                      first failure arrives as 1
	 * @param now          the clock reading to schedule from, passed in so one decision uses a single
	 *                      instant
	 */
	RetryVerdict decide(Throwable failure, int attemptsSoFar, Instant now);

	/**
	 * The attempt count past which nothing is retried, readable without provoking a decision.
	 */
	int getMaxAttempts();
}
