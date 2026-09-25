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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;

import org.junit.Before;
import org.junit.Test;

import com.adyen.commerce.connector.exception.BillingException;
import com.adyen.commerce.connector.exception.ConnectorNotConfiguredException;
import com.adyen.commerce.connector.exception.RetryableBillingException;
import com.adyen.commerce.connector.retry.RetryVerdict;

import de.hybris.bootstrap.annotations.UnitTest;

/**
 * Unit test for {@link DefaultBillingRetryPolicy}. The classification is the one
 * {@link com.adyen.commerce.connector.exception.BillingException} documents - {@code isRetryable()}
 * decides, and nothing else does - so these tests are mostly about it being applied.
 */
@UnitTest
public class DefaultBillingRetryPolicyTest
{
	private static final Instant NOW = Instant.parse("2026-08-20T12:00:00Z");

	private DefaultBillingRetryPolicy policy;

	@Before
	public void setUp()
	{
		policy = new DefaultBillingRetryPolicy();
		policy.setMaxAttempts(4);
		policy.setInitialBackoffSeconds(60L);
		policy.setBackoffMultiplier(4.0d);
		policy.setMaxBackoffSeconds(3600L);
	}

	@Test
	public void retriesATransientFailure()
	{
		final RetryVerdict verdict = policy.decide(new RetryableBillingException("Chargebee is down"), 1, NOW);

		assertTrue(verdict.retry());
		assertEquals(NOW.plusSeconds(60L), verdict.nextAttemptAt());
		assertNotNull(verdict.reason());
	}

	@Test
	public void backsOffExponentially()
	{
		final RetryableBillingException failure = new RetryableBillingException("still down");

		assertEquals(NOW.plusSeconds(60L), policy.decide(failure, 1, NOW).nextAttemptAt());
		assertEquals(NOW.plusSeconds(240L), policy.decide(failure, 2, NOW).nextAttemptAt());
		assertEquals(NOW.plusSeconds(960L), policy.decide(failure, 3, NOW).nextAttemptAt());
	}

	@Test
	public void capsTheBackoff()
	{
		policy.setMaxAttempts(20);

		assertEquals(NOW.plusSeconds(3600L), policy.decide(new RetryableBillingException("down"), 8, NOW)
				.nextAttemptAt());
	}

	@Test
	public void givesUpOnceTheAttemptsAreExhausted()
	{
		final RetryVerdict verdict = policy.decide(new RetryableBillingException("down"), 4, NOW);

		assertFalse(verdict.retry());
		assertNull(verdict.nextAttemptAt());
		assertTrue(verdict.reason().contains("exhausted"));
	}

	/**
	 * A failure that will fail identically on replay is not worth waiting out the backoff schedule.
	 */
	@Test
	public void givesUpAtOnceOnATerminalFailure()
	{
		final RetryVerdict verdict = policy.decide(new ConnectorNotConfiguredException("no connector"), 1, NOW);

		assertFalse(verdict.retry());
		assertTrue(verdict.reason().contains("terminal"));
	}

	@Test
	public void treatsAPlainBillingExceptionAsTerminal()
	{
		assertFalse(policy.decide(new BillingException("refused"), 1, NOW).retry());
	}

	/**
	 * An unclassified failure - a bug in the mapping, a database hiccup - is retried rather than
	 * dead-lettered on the spot. It costs one bounded series of attempts; the alternative costs a paid order
	 * its subscription on the strength of a stack trace nobody has read yet.
	 */
	@Test
	public void treatsAnUnclassifiedFailureAsRetryable()
	{
		assertTrue(policy.decide(new IllegalStateException("boom"), 1, NOW).retry());
	}

	@Test
	public void stillHonoursTheAttemptCapForUnclassifiedFailures()
	{
		assertFalse(policy.decide(new IllegalStateException("boom"), 4, NOW).retry());
	}

	@Test
	public void reportsItsOwnCutOff()
	{
		assertEquals(4, policy.getMaxAttempts());
	}
}
