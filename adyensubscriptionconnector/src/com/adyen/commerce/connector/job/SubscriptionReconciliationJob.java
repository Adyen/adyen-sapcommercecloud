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

public class SubscriptionReconciliationJob extends AbstractJobPerformable<CronJobModel>
{
	static final String STALE_AFTER_MINUTES = "adyen.subscription.reconciliation.staleAfterMinutes";
	static final String BATCH_SIZE = "adyen.subscription.reconciliation.batchSize";

	/**
	 * How long after its term ended an {@code EXPIRED} subscription is still re-read.
	 *
	 * <p>A platform can bring one back — Chargebee reactivates a {@code cancelled} subscription, Recurly one
	 * whose {@code current_period_ends_at} has not passed — so a lost webhook on a reactivation needs a later
	 * sweep to repair it. The window is bounded so the sweep converges: otherwise every subscription that ever
	 * ended stays a candidate for the lifetime of the store and spends the platform read budget on terms that
	 * finished years ago. It is measured from {@code currentPeriodEnd}, which is what bounds reactivation on
	 * Recurly and does not restart each time the reference is re-read.</p>
	 */
	static final String EXPIRED_WINDOW_HOURS = "adyen.subscription.reconciliation.expiredWindowHours";

	/**
	 * Statuses excluded from the sweep, so that it does not grow without bound as subscriptions end.
	 *
	 * <p>{@code FAILED} is the platform's terminal state for a subscription whose collection never succeeded
	 * (Recurly's {@code failed}), not a retryable activation error; retryable activation failures live on
	 * {@code BillingActivationAttempt.status} with its own retry job. Neither status drops a subscription that
	 * is still serving: a cancellation scheduled for the end of the term — Recurly's {@code canceled},
	 * Chargebee's {@code non_renewing} — normalizes to {@code ACTIVE} carrying {@code cancelAtPeriodEnd} and
	 * stays a candidate for the remainder of its term.</p>
	 *
	 * <p>{@code EXPIRED} is deliberately absent, because both platforms can reactivate a subscription that
	 * normalizes to it; it is bounded by a window instead — see {@link #EXPIRED_WINDOW_HOURS}.</p>
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
		// A term that ended before this is old enough to stop asking about. Zero hours means "only while the
		// term has not run out yet", the strictest setting that still catches a Recurly reactivation.
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

	/**
	 * Connector configuration is scoped to a base store. Cron-job sessions do not have a storefront site/store,
	 * so each reference must be reconciled in the context of the order that created it. The local view prevents
	 * one subscription's store from leaking into the next item in a multi-store batch.
	 */
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
	 * The references due for a re-read, oldest first.
	 *
	 * <p>A null status is admitted explicitly because {@code NOT IN} evaluates to null rather than true for a
	 * null left-hand side, and a never-synced reference is what the sweep exists to catch. The ordering
	 * spells out where nulls belong rather than leaving it to the database: Oracle sorts them last on an
	 * ascending sort while MySQL and SQL Server sort them first, which would make never-synced references the
	 * last thing a capped batch reaches on one deployment and the first on another.</p>
	 *
	 * @param endedAfter an {@code EXPIRED} reference stays a candidate while its {@code currentPeriodEnd} is
	 *        later than this. A null {@code currentPeriodEnd} does not qualify: with no term end the window
	 *        cannot be bounded, and the reference would stay in every sweep for good.
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
