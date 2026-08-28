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
package com.adyen.commerce.connector.job;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adyen.commerce.connector.activation.BillingActivationAttemptService;
import com.adyen.commerce.connector.model.BillingActivationAttemptModel;
import com.adyen.commerce.connector.model.BillingWebhookEventApplicationModel;
import com.adyen.commerce.connector.model.BillingWebhookEventModel;

import de.hybris.platform.cronjob.enums.CronJobResult;
import de.hybris.platform.cronjob.enums.CronJobStatus;
import de.hybris.platform.cronjob.model.CronJobModel;
import de.hybris.platform.servicelayer.cronjob.AbstractJobPerformable;
import de.hybris.platform.servicelayer.cronjob.PerformResult;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;

/**
 * Removes the billing journals once they have stopped being useful.
 *
 * <p>Every delivery and every activation leaves a row, none of them was ever removed, and one of the
 * columns is a long text. On a store that sells subscriptions these tables only grow, and the webhook
 * bodies now kept on failures make each row heavier than it was.</p>
 *
 * <h3>What it will not delete</h3>
 * <p>Two rules, and both are about not destroying evidence.</p>
 *
 * <p>Anything still actionable stays regardless of age: an activation that is {@code PENDING} or
 * {@code FAILED} is waiting for the retry job, and a delivery that has not settled may still be
 * redelivered. Deleting those would not tidy a queue, it would empty one.</p>
 *
 * <p>Anything that recorded a problem is kept far longer than an ordinary success. A dead letter means a
 * shopper paid and got nothing, and a delivery that failed is the only local trace that it arrived at
 * all - the platform's own delivery log ages out on its own schedule, usually a shorter one. So the
 * quiet successes go on the short window and everything carrying an error or a dead-letter stamp goes on
 * the long one.</p>
 *
 * <h3>Why the ordinary rows are recognised by their emptiness</h3>
 * <p>A webhook delivery is judged by {@code deadLetteredAt} and {@code lastError} being absent rather
 * than by its status string. The status vocabulary lives in the dispatcher, and a retention job that
 * repeated those literals here would keep deleting the right rows only until somebody renamed one - at
 * which point it would silently start deleting nothing, or worse, the wrong thing. Absent error and
 * absent dead letter is the same question asked in a way that cannot drift.</p>
 */
public class SubscriptionBillingRetentionJob extends AbstractJobPerformable<CronJobModel>
{
	private static final Logger LOG = LoggerFactory.getLogger(SubscriptionBillingRetentionJob.class);

	private FlexibleSearchService flexibleSearchService;
	private ModelService modelService;
	private Clock clock = Clock.systemUTC();
	private int batchSize = 500;
	private Duration settledAfter = Duration.ofDays(30);
	private Duration troubledAfter = Duration.ofDays(180);

	@Override
	public PerformResult perform(final CronJobModel cronJob)
	{
		final Instant now = clock.instant();
		final Date settledBefore = Date.from(now.minus(settledAfter));
		final Date troubledBefore = Date.from(now.minus(troubledAfter));

		int removed = 0;
		removed += removeWebhookEvents(cronJob, settledBefore, troubledBefore);
		if (isAbortRequested(cronJob))
		{
			return new PerformResult(CronJobResult.UNKNOWN, CronJobStatus.ABORTED);
		}
		removed += removeActivationAttempts(cronJob, settledBefore, troubledBefore);

		if (removed > 0)
		{
			LOG.info("Removed {} expired subscription billing journal row(s).", Integer.valueOf(removed));
		}
		return new PerformResult(CronJobResult.SUCCESS, CronJobStatus.FINISHED);
	}

	/**
	 * Applications go first and by their own query rather than as a side effect of the parent.
	 *
	 * <p>{@code BillingWebhookEventApplication.event} is a plain attribute, not a relation, so removing the
	 * delivery does not take its applications with it - it leaves rows pointing at nothing, on a mandatory
	 * reference. The parent is therefore only removed once its children are gone, and a batch that runs out
	 * of room mid-way leaves a delivery with fewer applications rather than an orphan, which the next run
	 * finishes.</p>
	 */
	protected int removeWebhookEvents(final CronJobModel cronJob, final Date settledBefore, final Date troubledBefore)
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery(
				"SELECT {pk} FROM {BillingWebhookEvent} WHERE "
						+ "({lastError} IS NULL AND {deadLetteredAt} IS NULL AND {receivedAt} < ?settledBefore) "
						+ "OR (({lastError} IS NOT NULL OR {deadLetteredAt} IS NOT NULL) AND {receivedAt} < ?troubledBefore) "
						+ "ORDER BY {receivedAt} ASC");
		query.addQueryParameter("settledBefore", settledBefore);
		query.addQueryParameter("troubledBefore", troubledBefore);
		query.setCount(batchSize);

		final List<BillingWebhookEventModel> expired = flexibleSearchService
				.<BillingWebhookEventModel> search(query).getResult();

		int removed = 0;
		for (final BillingWebhookEventModel event : expired)
		{
			if (isAbortRequested(cronJob))
			{
				return removed;
			}
			removed += removeApplicationsOf(event);
			modelService.remove(event);
			removed++;
		}
		return removed;
	}

	protected int removeApplicationsOf(final BillingWebhookEventModel event)
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery(
				"SELECT {pk} FROM {BillingWebhookEventApplication} WHERE {event} = ?event");
		query.addQueryParameter("event", event);

		final List<BillingWebhookEventApplicationModel> applications = flexibleSearchService
				.<BillingWebhookEventApplicationModel> search(query).getResult();
		applications.forEach(modelService::remove);
		return applications.size();
	}

	/**
	 * The activation journal, on the same two windows.
	 *
	 * <p>Here the statuses can be named, because they are constants on the service's own interface rather
	 * than literals borrowed from an implementation. {@code PENDING} and {@code FAILED} are absent on
	 * purpose: the first is in flight and the second is the retry job's queue.</p>
	 */
	protected int removeActivationAttempts(final CronJobModel cronJob, final Date settledBefore,
			final Date troubledBefore)
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery(
				"SELECT {pk} FROM {BillingActivationAttempt} WHERE "
						+ "({status} IN (?settled) AND {lastAttemptAt} < ?settledBefore) "
						+ "OR ({status} = ?deadLetter AND {lastAttemptAt} < ?troubledBefore) "
						+ "ORDER BY {lastAttemptAt} ASC");
		query.addQueryParameter("settled",
				List.of(BillingActivationAttemptService.STATUS_SUCCEEDED,
						BillingActivationAttemptService.STATUS_NOT_APPLICABLE));
		query.addQueryParameter("deadLetter", BillingActivationAttemptService.STATUS_DEAD_LETTER);
		query.addQueryParameter("settledBefore", settledBefore);
		query.addQueryParameter("troubledBefore", troubledBefore);
		query.setCount(batchSize);

		final List<BillingActivationAttemptModel> expired = flexibleSearchService
				.<BillingActivationAttemptModel> search(query).getResult();

		int removed = 0;
		for (final BillingActivationAttemptModel attempt : expired)
		{
			if (isAbortRequested(cronJob))
			{
				return removed;
			}
			modelService.remove(attempt);
			removed++;
		}
		return removed;
	}

	protected boolean isAbortRequested(final CronJobModel cronJob)
	{
		if (clearAbortRequestedIfNeeded(cronJob))
		{
			LOG.info("Abort requested; stopping the retention sweep part-way. The next run resumes where this left off.");
			return true;
		}
		return false;
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}

	public void setClock(final Clock clock)
	{
		this.clock = clock;
	}

	public void setBatchSize(final int batchSize)
	{
		this.batchSize = batchSize;
	}

	public void setSettledAfterDays(final int settledAfterDays)
	{
		this.settledAfter = Duration.ofDays(settledAfterDays);
	}

	public void setTroubledAfterDays(final int troubledAfterDays)
	{
		this.troubledAfter = Duration.ofDays(troubledAfterDays);
	}
}
