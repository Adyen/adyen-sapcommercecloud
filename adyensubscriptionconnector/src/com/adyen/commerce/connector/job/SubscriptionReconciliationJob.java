package com.adyen.commerce.connector.job;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.configuration2.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adyen.commerce.connector.context.SubscriptionBaseStoreSelectorStrategy;
import com.adyen.commerce.connector.dto.NormalizedSubscriptionStatus;
import com.adyen.commerce.connector.exception.BillingException;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;
import com.adyen.commerce.connector.reconciliation.SubscriptionReconciliationService;

import de.hybris.platform.basecommerce.model.site.BaseSiteModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.cronjob.enums.CronJobResult;
import de.hybris.platform.cronjob.enums.CronJobStatus;
import de.hybris.platform.cronjob.model.CronJobModel;
import de.hybris.platform.site.BaseSiteService;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.cronjob.AbstractJobPerformable;
import de.hybris.platform.servicelayer.cronjob.PerformResult;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.session.SessionExecutionBody;
import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.store.BaseStoreModel;

/** Re-reads stale subscription references from their platform, oldest first. */
public class SubscriptionReconciliationJob extends AbstractJobPerformable<CronJobModel>
{
	static final String STALE_AFTER_MINUTES = "adyen.subscription.reconciliation.staleAfterMinutes";
	static final String BATCH_SIZE = "adyen.subscription.reconciliation.batchSize";

	/**
	 * How long after {@code currentPeriodEnd} an {@code EXPIRED} subscription is still re-read: both platforms can
	 * reactivate one, and the bound keeps ended subscriptions from staying candidates forever.
	 */
	static final String EXPIRED_WINDOW_HOURS = "adyen.subscription.reconciliation.expiredWindowHours";

	/**
	 * Statuses never re-read. {@code EXPIRED} is bounded by {@link #EXPIRED_WINDOW_HOURS} instead, and a
	 * subscription cancelled at period end is still {@code ACTIVE} until the term ends.
	 */
	static final List<String> TERMINAL_STATUSES = List.of(
			NormalizedSubscriptionStatus.CANCELLED.name(),
			NormalizedSubscriptionStatus.FAILED.name());

	private static final Logger LOG = LoggerFactory.getLogger(SubscriptionReconciliationJob.class);
	private static final int DEFAULT_STALE_AFTER_MINUTES = 60;
	private static final int DEFAULT_BATCH_SIZE = 100;
	private static final int DEFAULT_EXPIRED_WINDOW_HOURS = 168;

	private FlexibleSearchService flexibleSearchService;
	private SubscriptionReconciliationService reconciliationService;
	private ConfigurationService configurationService;
	private SessionService sessionService;
	private BaseSiteService baseSiteService;
	private Clock clock = Clock.systemUTC();

	@Override
	public PerformResult perform(final CronJobModel cronJob)
	{
		final Configuration configuration = configurationService.getConfiguration();
		final int staleAfterMinutes = Math.max(1,
				configuration.getInt(STALE_AFTER_MINUTES, DEFAULT_STALE_AFTER_MINUTES));
		final int batchSize = Math.max(1, configuration.getInt(BATCH_SIZE, DEFAULT_BATCH_SIZE));
		final int expiredWindowHours = Math.max(0,
				configuration.getInt(EXPIRED_WINDOW_HOURS, DEFAULT_EXPIRED_WINDOW_HOURS));
		final Instant now = clock.instant();
		final Instant staleBefore = now.minus(staleAfterMinutes, ChronoUnit.MINUTES);
		// Zero hours means "only while the term has not run out yet".
		final Instant endedAfter = now.minus(expiredWindowHours, ChronoUnit.HOURS);
		boolean failed = false;
		for (final BillingSubscriptionRefModel subscription : findCandidates(staleBefore, endedAfter, batchSize))
		{
			if (clearAbortRequestedIfNeeded(cronJob))
			{
				return new PerformResult(CronJobResult.UNKNOWN, CronJobStatus.ABORTED);
			}
			try
			{
				reconcileInOrderContext(subscription);
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

	/** Reconciles in the store of the originating order; the local view keeps it from leaking to the next item. */
	protected void reconcileInOrderContext(final BillingSubscriptionRefModel subscription) throws BillingException
	{
		final AbstractOrderModel order = subscription.getOrder();
		final BaseStoreModel store = order == null ? null : order.getStore();
		if (store == null)
		{
			throw new IllegalStateException("Subscription " + subscription.getExternalSubscriptionId()
					+ " has no originating order/base store; connector configuration cannot be selected safely");
		}

		final Map<String, Object> sessionParameters = Collections.singletonMap(
				SubscriptionBaseStoreSelectorStrategy.CURRENT_SUBSCRIPTION_BASE_STORE, store);
		final Object result = sessionService.executeInLocalViewWithParams(sessionParameters, new SessionExecutionBody()
		{
			@Override
			public Object execute()
			{
				final BaseSiteModel site = order.getSite();
				if (site != null)
				{
					baseSiteService.setCurrentBaseSite(site, false);
				}
				try
				{
					return reconciliationService.reconcile(subscription);
				}
				catch (final BillingException exception)
				{
					return exception;
				}
			}
		});

		if (result instanceof BillingException)
		{
			throw (BillingException) result;
		}
	}

	/**
	 * The references due for a re-read, never-synced first. Null status is admitted explicitly, as
	 * {@code NOT IN} is not true for null, and null ordering is spelled out because databases differ on it.
	 *
	 * @param endedAfter an {@code EXPIRED} reference stays a candidate while its {@code currentPeriodEnd} is later
	 */
	protected List<BillingSubscriptionRefModel> findCandidates(final Instant staleBefore, final Instant endedAfter,
			final int batchSize)
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery(
				"SELECT {pk} FROM {BillingSubscriptionRef} "
						+ "WHERE ({status} IS NULL OR {status} NOT IN (?terminalStatuses)) "
						+ "AND ({status} IS NULL OR {status} <> ?expired "
						+ "OR ({currentPeriodEnd} IS NOT NULL AND {currentPeriodEnd} > ?endedAfter)) "
						+ "AND ({status} = ?pastDue "
						+ "OR {lastSyncedAt} IS NULL "
						+ "OR {lastSyncedAt} < ?staleBefore) "
						+ "ORDER BY CASE WHEN {lastSyncedAt} IS NULL THEN 0 ELSE 1 END ASC, {lastSyncedAt} ASC");

		query.addQueryParameter("terminalStatuses", TERMINAL_STATUSES);
		query.addQueryParameter("expired", NormalizedSubscriptionStatus.EXPIRED.name());
		query.addQueryParameter("endedAfter", Date.from(endedAfter));
		query.addQueryParameter("pastDue", NormalizedSubscriptionStatus.PAST_DUE.name());
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

	public void setSessionService(final SessionService sessionService)
	{
		this.sessionService = sessionService;
	}

	public void setBaseSiteService(final BaseSiteService baseSiteService)
	{
		this.baseSiteService = baseSiteService;
	}

	void setClock(final Clock clock)
	{
		this.clock = clock;
	}
}
