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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adyen.commerce.connector.activation.BillingActivationAttemptService;
import com.adyen.commerce.connector.activation.SubscriptionOrderActivator;
import com.adyen.commerce.connector.model.BillingActivationAttemptModel;

import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.cronjob.enums.CronJobResult;
import de.hybris.platform.cronjob.enums.CronJobStatus;
import de.hybris.platform.cronjob.model.CronJobModel;
import de.hybris.platform.servicelayer.cronjob.AbstractJobPerformable;
import de.hybris.platform.servicelayer.cronjob.PerformResult;

/**
 * Drives the retry half of the policy: picks up activation attempts that are due and runs them again.
 *
 * <p>It holds no logic of its own about how to activate anything — it hands each order back to
 * {@link SubscriptionOrderActivator}, which is idempotent, journals its own outcome and establishes its
 * own store context. The job's only job is choosing what to hand back, and noticing when handing it back
 * achieved nothing.</p>
 *
 * <h3>Why it has to notice</h3>
 * <p>The activator declines quietly in several legitimate cases: the store's billing platform has since
 * been switched off, the product is no longer mapped to a plan, the order no longer carries a
 * subscription product. A declined retry leaves the record exactly as it found it — still due, still in
 * this queue — and every subsequent run would pick it up again for ever. So a pass that did not raise
 * the attempt count is treated as unactionable and dead-lettered with that as the reason, which both
 * empties the queue and tells the operator something they would otherwise never learn.</p>
 */
public class SubscriptionActivationRetryJob extends AbstractJobPerformable<CronJobModel>
{
	private static final Logger LOG = LoggerFactory.getLogger(SubscriptionActivationRetryJob.class);

	private BillingActivationAttemptService attemptService;
	private SubscriptionOrderActivator subscriptionOrderActivator;
	private Clock clock = Clock.systemUTC();
	private int batchSize = 100;
	private Duration stalePendingAfter = Duration.ofMinutes(30);

	@Override
	public PerformResult perform(final CronJobModel cronJob)
	{
		final Instant now = clock.instant();
		final List<BillingActivationAttemptModel> due = attemptService.findDue(now, now.minus(stalePendingAfter),
				batchSize);
		if (due.isEmpty())
		{
			return new PerformResult(CronJobResult.SUCCESS, CronJobStatus.FINISHED);
		}

		LOG.info("Retrying {} due subscription activation(s).", due.size());
		for (final BillingActivationAttemptModel attempt : due)
		{
			if (clearAbortRequestedIfNeeded(cronJob))
			{
				LOG.info("Abort requested; stopping with {} activation(s) of this batch unprocessed.", due.size());
				return new PerformResult(CronJobResult.UNKNOWN, CronJobStatus.ABORTED);
			}
			retry(attempt);
		}

		if (due.size() >= batchSize)
		{
			// Said out loud rather than left to be inferred from the batch size: a queue that is permanently
			// longer than one batch is a different problem from a handful of orders waiting their turn.
			LOG.info("The batch limit of {} was reached; more activations may still be waiting.", batchSize);
		}
		return new PerformResult(CronJobResult.SUCCESS, CronJobStatus.FINISHED);
	}

	protected void retry(final BillingActivationAttemptModel attempt)
	{
		try
		{
			// AbstractOrder on the type, so a cart is representable even though nothing creates one here.
			if (!(attempt.getOrder() instanceof OrderModel order))
			{
				attemptService.abandon(attempt, "the attempt points at " + (attempt.getOrder() == null ? "no order"
						: "a " + attempt.getOrder().getItemtype() + " rather than an Order") + ", so it cannot be retried");
				return;
			}

			final int before = attemptCount(attempt);
			subscriptionOrderActivator.activateFor(order);
			modelService.refresh(attempt);

			if (attemptCount(attempt) == before && !isSettled(attempt))
			{
				attemptService.abandon(attempt, "the retry reached no billing platform — the order no longer resolves "
						+ "to a subscription activation for this store, so nothing was attempted and nothing will be");
			}
		}
		catch (final RuntimeException e)
		{
			// One unreadable row must not cost the rest of the batch its turn.
			LOG.error("Failed to retry the activation attempt for order '{}'; leaving it queued.",
					attempt.getOrder() == null ? null : attempt.getOrder().getCode(), e);
		}
	}

	/**
	 * Whether the attempt has reached a state the job is done with. {@code PENDING} counts as unsettled on
	 * purpose: it means the activator opened a record and never closed it, which is the same dead end as a
	 * retry that never ran.
	 */
	protected boolean isSettled(final BillingActivationAttemptModel attempt)
	{
		return !BillingActivationAttemptService.STATUS_FAILED.equals(attempt.getStatus())
				&& !BillingActivationAttemptService.STATUS_PENDING.equals(attempt.getStatus());
	}

	@Override
	public boolean isAbortable()
	{
		return true;
	}

	/**
	 * Read once, not twice. The count is compared against itself across a refresh, so a helper that reads
	 * it twice per call can straddle the refresh and report a change as no change — which here means
	 * dead-lettering an activation that was in fact retried.
	 */
	protected static int attemptCount(final BillingActivationAttemptModel attempt)
	{
		final Integer count = attempt.getAttemptCount();
		return count == null ? 0 : count.intValue();
	}

	public void setAttemptService(final BillingActivationAttemptService attemptService)
	{
		this.attemptService = attemptService;
	}

	public void setSubscriptionOrderActivator(final SubscriptionOrderActivator subscriptionOrderActivator)
	{
		this.subscriptionOrderActivator = subscriptionOrderActivator;
	}

	public void setClock(final Clock clock)
	{
		this.clock = clock;
	}

	public void setBatchSize(final int batchSize)
	{
		this.batchSize = batchSize;
	}

	public void setStalePendingAfterSeconds(final long stalePendingAfterSeconds)
	{
		this.stalePendingAfter = Duration.ofSeconds(stalePendingAfterSeconds);
	}
}
