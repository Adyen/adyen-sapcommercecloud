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
 * Hands due activation attempts back to the idempotent {@link SubscriptionOrderActivator}. A retry the
 * activator quietly declined (platform switched off, product unmapped) does not raise the attempt count and is
 * dead-lettered, as it would otherwise stay due forever.
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
			LOG.info("The batch limit of {} was reached; more activations may still be waiting.", batchSize);
		}
		return new PerformResult(CronJobResult.SUCCESS, CronJobStatus.FINISHED);
	}

	protected void retry(final BillingActivationAttemptModel attempt)
	{
		try
		{
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

	/** {@code PENDING} is unsettled: the activator opened the record and never closed it. */
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

	/** Reads the count once, as it is compared across a model refresh. */
	protected static int attemptCount(final BillingActivationAttemptModel attempt)
	{
		final Integer count = attempt.getAttemptCount();
		return count == null ? 0 : count;
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
