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
package com.adyen.commerce.connector.activation.job;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adyen.commerce.connector.activation.BillingActivationAttemptService;
import com.adyen.commerce.connector.activation.SubscriptionOrderActivator;
import com.adyen.commerce.connector.model.BillingActivationAttemptModel;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.cronjob.enums.CronJobResult;
import de.hybris.platform.cronjob.enums.CronJobStatus;
import de.hybris.platform.cronjob.model.CronJobModel;
import de.hybris.platform.servicelayer.cronjob.PerformResult;
import de.hybris.platform.servicelayer.model.ModelService;

/**
 * Unit test for {@link SubscriptionActivationRetryJob}. The job holds no activation logic of its own, so
 * what is worth testing is what it chooses to hand back — and what it does when handing something back
 * changes nothing, which is the difference between an emptying queue and a permanent one.
 */
@UnitTest
public class SubscriptionActivationRetryJobTest
{
	private static final Instant NOW = Instant.parse("2026-08-20T12:00:00Z");
	private static final long STALE_AFTER_SECONDS = 1800L;

	@Mock
	private BillingActivationAttemptService attemptService;
	@Mock
	private SubscriptionOrderActivator activator;
	@Mock
	private ModelService modelService;
	@Mock
	private CronJobModel cronJob;
	@Mock
	private OrderModel order;

	private SubscriptionActivationRetryJob job;

	@Before
	public void setUp()
	{
		MockitoAnnotations.openMocks(this);

		job = new SubscriptionActivationRetryJob();
		job.setAttemptService(attemptService);
		job.setSubscriptionOrderActivator(activator);
		job.setModelService(modelService);
		job.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
		job.setBatchSize(10);
		job.setStalePendingAfterSeconds(STALE_AFTER_SECONDS);
	}

	@Test
	public void asksForWhatIsDueAndForStalePendingAttempts()
	{
		when(attemptService.findDue(any(), any(), anyInt())).thenReturn(List.of());

		final PerformResult result = job.perform(cronJob);

		verify(attemptService).findDue(NOW, NOW.minusSeconds(STALE_AFTER_SECONDS), 10);
		assertEquals(CronJobResult.SUCCESS, result.getResult());
		assertEquals(CronJobStatus.FINISHED, result.getStatus());
	}

	@Test
	public void handsEachDueOrderBackToTheActivator()
	{
		final BillingActivationAttemptModel attempt = attempt(1, BillingActivationAttemptService.STATUS_FAILED);
		givenDue(attempt);
		// A retry that reached the platform: the activator opened a new attempt, so the count moved.
		when(attempt.getAttemptCount()).thenReturn(Integer.valueOf(1), Integer.valueOf(2));

		job.perform(cronJob);

		verify(activator).activateFor(order);
		verify(attemptService, never()).abandon(any(), any());
	}

	/**
	 * The activator declines quietly when the store's platform has been switched off, or the product is
	 * no longer mapped. Left alone, such a row is re-read on every run of the job for ever.
	 */
	@Test
	public void abandonsARetryThatChangedNothing()
	{
		final BillingActivationAttemptModel attempt = attempt(1, BillingActivationAttemptService.STATUS_FAILED);
		givenDue(attempt);

		job.perform(cronJob);

		verify(activator).activateFor(order);
		verify(attemptService).abandon(eq(attempt), contains("no billing platform"));
	}

	@Test
	public void leavesASucceededRetryAlone()
	{
		final BillingActivationAttemptModel attempt = attempt(1, BillingActivationAttemptService.STATUS_SUCCEEDED);
		givenDue(attempt);

		job.perform(cronJob);

		verify(attemptService, never()).abandon(any(), any());
	}

	@Test
	public void leavesAnAlreadyDeadLetteredRetryAlone()
	{
		final BillingActivationAttemptModel attempt = attempt(1, BillingActivationAttemptService.STATUS_DEAD_LETTER);
		givenDue(attempt);

		job.perform(cronJob);

		verify(attemptService, never()).abandon(any(), any());
	}

	/**
	 * PENDING with an unmoved count is the same dead end as a retry that never ran: the activator opened a
	 * record and never closed it.
	 */
	@Test
	public void abandonsARetryLeftPending()
	{
		final BillingActivationAttemptModel attempt = attempt(1, BillingActivationAttemptService.STATUS_PENDING);
		givenDue(attempt);

		job.perform(cronJob);

		verify(attemptService).abandon(eq(attempt), any());
	}

	@Test
	public void abandonsAnAttemptThatIsNotOnAnOrder()
	{
		final BillingActivationAttemptModel attempt = attempt(1, BillingActivationAttemptService.STATUS_FAILED);
		when(attempt.getOrder()).thenReturn(null);
		givenDue(attempt);

		job.perform(cronJob);

		verify(activator, never()).activateFor(any());
		verify(attemptService).abandon(eq(attempt), contains("no order"));
	}

	@Test
	public void abandonsAnAttemptOnACartRatherThanAnOrder()
	{
		final CartModel cart = mock(CartModel.class);
		when(cart.getItemtype()).thenReturn("Cart");
		final BillingActivationAttemptModel attempt = attempt(1, BillingActivationAttemptService.STATUS_FAILED);
		when(attempt.getOrder()).thenReturn(cart);
		givenDue(attempt);

		job.perform(cronJob);

		verify(activator, never()).activateFor(any());
		verify(attemptService).abandon(eq(attempt), contains("Cart"));
	}

	/**
	 * One unreadable row must not cost the rest of the batch its turn, and must leave that row queued
	 * rather than quietly dead-lettered on the strength of a bug here.
	 */
	@Test
	public void carriesOnAfterOneRowFails()
	{
		final BillingActivationAttemptModel broken = attempt(1, BillingActivationAttemptService.STATUS_FAILED);
		final BillingActivationAttemptModel healthy = attempt(1, BillingActivationAttemptService.STATUS_SUCCEEDED);
		when(attemptService.findDue(any(), any(), anyInt())).thenReturn(List.of(broken, healthy));
		doThrow(new IllegalStateException("detached")).when(modelService).refresh(broken);

		final PerformResult result = job.perform(cronJob);

		// Both were tried; the broken one is left queued rather than dead-lettered on the strength of a
		// failure that happened here rather than at the platform.
		verify(activator, times(2)).activateFor(order);
		verify(attemptService, never()).abandon(eq(broken), any());
		assertEquals(CronJobResult.SUCCESS, result.getResult());
	}

	@Test
	public void stopsWhenAnAbortIsRequested()
	{
		when(cronJob.getRequestAbort()).thenReturn(Boolean.TRUE);
		givenDue(attempt(1, BillingActivationAttemptService.STATUS_FAILED));

		final PerformResult result = job.perform(cronJob);

		verify(activator, never()).activateFor(any());
		assertEquals(CronJobStatus.ABORTED, result.getStatus());
	}

	@Test
	public void isAbortable()
	{
		org.junit.Assert.assertTrue(job.isAbortable());
	}

	// ---------------------------------------------------------------- helpers

	private void givenDue(final BillingActivationAttemptModel attempt)
	{
		when(attemptService.findDue(any(), any(), anyInt())).thenReturn(List.of(attempt));
	}

	private BillingActivationAttemptModel attempt(final int attemptCount, final String status)
	{
		final BillingActivationAttemptModel attempt = mock(BillingActivationAttemptModel.class);
		when(attempt.getAttemptCount()).thenReturn(Integer.valueOf(attemptCount));
		when(attempt.getStatus()).thenReturn(status);
		when(attempt.getOrder()).thenReturn(order);
		return attempt;
	}
}
