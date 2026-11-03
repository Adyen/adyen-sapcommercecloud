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
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;

/**
 * Removes old billing journal rows: settled ones after a short window, ones with an error or a dead letter after
 * a long one, as they may be the only trace that a shopper paid and got nothing. Rows still actionable
 * ({@code PENDING}, {@code FAILED}) are kept.
 */
public class SubscriptionBillingRetentionJob extends AbstractJobPerformable<CronJobModel>
{
	private static final Logger LOG = LoggerFactory.getLogger(SubscriptionBillingRetentionJob.class);

	private FlexibleSearchService flexibleSearchService;
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
			LOG.info("Removed {} expired subscription billing journal row(s).", removed);
		}
		return new PerformResult(CronJobResult.SUCCESS, CronJobStatus.FINISHED);
	}

	/** Applications go first: {@code BillingWebhookEventApplication.event} is a plain mandatory attribute. */
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

	/** The activation journal, on the same two windows. */
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
