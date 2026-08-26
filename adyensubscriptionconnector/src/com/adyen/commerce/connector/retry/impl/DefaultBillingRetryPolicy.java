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
 * <p>With the shipped defaults an activation that keeps failing transiently is tried after 1 minute,
 * 4 minutes, 16 minutes and 64 minutes and is then dead-lettered — about an hour and a half of patience
 * in five attempts. That is deliberately slow: the failure this exists for is a billing platform being
 * down, and hammering a platform that is already struggling is how a short outage turns into a long one.
 * The cap stops the last interval of a longer schedule from stretching into days.</p>
 *
 * <p>No jitter. Attempts are scheduled from each order's own failure time, not from a shared tick, so
 * they are already spread out; adding randomness would only make the schedule impossible to assert on.</p>
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
	 * A failure is terminal when the connector said so. Anything that is not a {@link BillingException} —
	 * an NPE in our own mapping code, a database hiccup — is treated as retryable instead: it is by
	 * definition an unclassified failure, and one bounded series of retries costs less than silently
	 * dead-lettering an order the shopper has already paid for. The attempt cap stops that leniency from
	 * turning a genuine bug into an endless loop.
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
		// Computed in double and clamped rather than multiplied out in longs: a generous maxAttempts with a
		// large multiplier overflows a long duration long before it reaches anything a person would call a
		// schedule, and the cap makes the overflowed value indistinguishable from the intended one anyway.
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
