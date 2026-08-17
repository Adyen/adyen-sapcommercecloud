package com.adyen.commerce.connector.job;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import org.apache.commons.configuration2.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adyen.commerce.connector.exception.BillingException;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;
import com.adyen.commerce.connector.reconciliation.SubscriptionReconciliationService;

import de.hybris.platform.cronjob.enums.CronJobResult;
import de.hybris.platform.cronjob.enums.CronJobStatus;
import de.hybris.platform.cronjob.model.CronJobModel;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.cronjob.AbstractJobPerformable;
import de.hybris.platform.servicelayer.cronjob.PerformResult;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;

public class SubscriptionReconciliationJob extends AbstractJobPerformable<CronJobModel>
{
	static final String STALE_AFTER_MINUTES = "subscription.reconciliation.staleAfterMinutes";
	static final String BATCH_SIZE = "subscription.reconciliation.batchSize";
	private static final Logger LOG = LoggerFactory.getLogger(SubscriptionReconciliationJob.class);
	private static final int DEFAULT_STALE_AFTER_MINUTES = 60;
	private static final int DEFAULT_BATCH_SIZE = 100;

	private FlexibleSearchService flexibleSearchService;
	private SubscriptionReconciliationService reconciliationService;
	private ConfigurationService configurationService;
	private Clock clock = Clock.systemUTC();

	@Override
	public PerformResult perform(final CronJobModel cronJob)
	{
		final Configuration configuration = configurationService.getConfiguration();
		final int staleAfterMinutes = Math.max(1,
				configuration.getInt(STALE_AFTER_MINUTES, DEFAULT_STALE_AFTER_MINUTES));
		final int batchSize = Math.max(1, configuration.getInt(BATCH_SIZE, DEFAULT_BATCH_SIZE));
		final Instant staleBefore = clock.instant().minus(staleAfterMinutes, ChronoUnit.MINUTES);
		boolean failed = false;
		for (final BillingSubscriptionRefModel subscription : findCandidates(staleBefore, batchSize))
		{
			if (clearAbortRequestedIfNeeded(cronJob))
			{
				return new PerformResult(CronJobResult.UNKNOWN, CronJobStatus.ABORTED);
			}
			try
			{
				reconciliationService.reconcile(subscription);
			}
			catch (final BillingException | RuntimeException exception)
			{
				failed = true;
				LOG.error("Could not reconcile {} subscription {}", subscription.getPlatform(),
						subscription.getExternalSubscriptionId(), exception);
			}
		}
		return new PerformResult(failed ? CronJobResult.ERROR : CronJobResult.SUCCESS, CronJobStatus.FINISHED);
	}

	protected List<BillingSubscriptionRefModel> findCandidates(final Instant staleBefore, final int batchSize)
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery(
				"SELECT {pk} FROM {BillingSubscriptionRef} "
						+ "WHERE {status} = ?pastDue "
						+ "OR {lastSyncedAt} IS NULL "
						+ "OR {lastSyncedAt} < ?staleBefore "
						+ "ORDER BY {lastSyncedAt} ASC");

		query.addQueryParameter("pastDue", "PAST_DUE");
		query.addQueryParameter("staleBefore", Date.from(staleBefore));
		query.setCount(batchSize);
		query.setNeedTotal(false);

		return flexibleSearchService
				.<BillingSubscriptionRefModel>search(query)
				.getResult();
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}

	public void setReconciliationService(final SubscriptionReconciliationService reconciliationService)
	{
		this.reconciliationService = reconciliationService;
	}

	public void setConfigurationService(final ConfigurationService configurationService)
	{
		this.configurationService = configurationService;
	}

	void setClock(final Clock clock)
	{
		this.clock = clock;
	}
}
