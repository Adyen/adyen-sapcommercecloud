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
package com.adyen.commerce.connector.retry.impl;

import java.time.Duration;
import java.time.Instant;

import com.adyen.commerce.connector.exception.BillingException;
import com.adyen.commerce.connector.retry.BillingRetryPolicy;
import com.adyen.commerce.connector.retry.RetryVerdict;

/**
 * Capped exponential backoff over a bounded number of attempts.
 *
 * <p>With the defaults, an activation that keeps failing transiently is retried after 1, 4, 16 and 64
 * minutes and then dead-lettered. The slowness is deliberate: the failure this exists for is a billing
 * platform being down, and the cap stops the last interval of a longer schedule from stretching into
 * days.</p>
 *
 * <p>No jitter: attempts are scheduled from each order's own failure time rather than a shared tick, so
 * they are already spread out.</p>
 */
public class DefaultBillingRetryPolicy implements BillingRetryPolicy
{
	private int maxAttempts = 5;
	private Duration initialBackoff = Duration.ofMinutes(1);
	private double backoffMultiplier = 4.0d;
	private Duration maxBackoff = Duration.ofHours(6);

	@Override
	public RetryVerdict decide(final Throwable failure, final int attemptsSoFar, final Instant now)
	{
		if (isTerminal(failure))
		{
			return RetryVerdict.giveUp("terminal failure (" + describe(failure) + "); retrying would fail the same way");
		}
		if (attemptsSoFar >= maxAttempts)
		{
			return RetryVerdict.giveUp("exhausted " + maxAttempts + " attempts; last failure " + describe(failure));
		}
		final Duration backoff = backoffFor(attemptsSoFar);
		return RetryVerdict.retryAt(now.plus(backoff),
				"attempt " + attemptsSoFar + " of " + maxAttempts + " failed transiently; retrying in " + backoff);
	}

	/**
	 * A failure is terminal when the connector said so. Anything that is not a {@link BillingException} is an
	 * unclassified failure and is treated as retryable, because a bounded series of retries costs less than
	 * dead-lettering an order the shopper has already paid for; the attempt cap bounds it.
	 */
	protected boolean isTerminal(final Throwable failure)
	{
		return failure instanceof BillingException billingException && !billingException.isRetryable();
	}

	/**
	 * Backoff before the attempt that follows {@code attemptsSoFar} failures, so the first retry waits
	 * {@code initialBackoff}.
	 */
	protected Duration backoffFor(final int attemptsSoFar)
	{
		final int exponent = Math.max(0, attemptsSoFar - 1);
		// Computed in double and clamped rather than multiplied out in longs, which a generous maxAttempts
		// with a large multiplier overflows.
		final double scaled = initialBackoff.toMillis() * Math.pow(backoffMultiplier, exponent);
		if (scaled >= maxBackoff.toMillis())
		{
			return maxBackoff;
		}
		return Duration.ofMillis((long) scaled);
	}

	protected static String describe(final Throwable failure)
	{
		return failure == null ? "unknown" : failure.getClass().getSimpleName();
	}

	@Override
	public int getMaxAttempts()
	{
		return maxAttempts;
	}

	public void setMaxAttempts(final int maxAttempts)
	{
		this.maxAttempts = maxAttempts;
	}

	public void setInitialBackoffSeconds(final long initialBackoffSeconds)
	{
		this.initialBackoff = Duration.ofSeconds(initialBackoffSeconds);
	}

	public void setBackoffMultiplier(final double backoffMultiplier)
	{
		this.backoffMultiplier = backoffMultiplier;
	}

	public void setMaxBackoffSeconds(final long maxBackoffSeconds)
	{
		this.maxBackoff = Duration.ofSeconds(maxBackoffSeconds);
	}
}
