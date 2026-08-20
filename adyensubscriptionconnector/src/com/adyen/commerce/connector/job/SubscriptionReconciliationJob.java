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
	 * Statuses excluded from the sweep because re-reading them is not expected to be worth the platform call.
	 * Without this exclusion every subscription that ever ended stays in the sweep for the lifetime of the
	 * store: {@code lastSyncedAt} keeps ageing past the staleness threshold, so the set only grows, and
	 * eventually a whole batch — and the platform read-rate budget behind it — is spent re-confirming
	 * subscriptions that ended months ago while genuinely stale ones wait for a later run.
	 *
	 * <p>{@code FAILED} is in here for the same reason as the other two, even though it reads like a
	 * transient error: it is the platform's terminal state for a subscription whose collection never
	 * succeeded (Recurly's {@code failed}), not a retryable activation error. Retryable activation failures
	 * live on {@code BillingActivationAttempt.status}, which is a different type with its own retry job.</p>
	 *
	 * <p>Excluding {@code CANCELLED} does not drop a subscription that is still serving its customer: no
	 * connector maps one to it. A cancellation that is merely scheduled for the end of the term — Recurly's
	 * {@code canceled}, Chargebee's {@code non_renewing} — normalizes to {@code ACTIVE} carrying
	 * {@code cancelAtPeriodEnd}, so that reference stays a sweep candidate for the whole remainder of its
	 * term and a dropped webhook on it is still repaired by a later run.</p>
	 *
	 * <p>One real gap survives that, on Chargebee only. Chargebee's {@code cancelled} maps to
	 * {@code CANCELLED} and Chargebee can reactivate such a subscription, so the platform <em>does</em> leave
	 * this status and a re-fetch could return something new — but the reference is no longer a candidate, so a
	 * reactivation whose webhook is lost is never repaired here. Recurly is unaffected: nothing it reports maps
	 * to {@code CANCELLED} at all. Closing it means either keeping {@code CANCELLED} eligible for some window
	 * after {@code currentPeriodEnd} or leaning on the webhook alone; both are decisions, not oversights.</p>
	 */
	static final List<String> TERMINAL_STATUSES = List.of(
			NormalizedSubscriptionStatus.CANCELLED.name(),
			NormalizedSubscriptionStatus.EXPIRED.name(),
			NormalizedSubscriptionStatus.FAILED.name());

	private static final Logger LOG = LoggerFactory.getLogger(SubscriptionReconciliationJob.class);
	private static final int DEFAULT_STALE_AFTER_MINUTES = 60;
	private static final int DEFAULT_BATCH_SIZE = 100;

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
	 * The status column holds {@link NormalizedSubscriptionStatus} names — every writer sets it from the
	 * enum — so the parameters are taken from the enum too rather than repeated as literals here.
	 *
	 * <p>A null status has to be admitted explicitly: {@code NOT IN} evaluates to null, not true, for a null
	 * left-hand side, so a reference whose status was never written would otherwise be filtered out, and
	 * a never-synced reference is exactly what the sweep exists to catch.</p>
	 *
	 * <p>The ordering spells out where nulls belong instead of leaving it to the database. Oracle sorts
	 * nulls last on an ascending sort and MySQL and SQL Server sort them first, which would silently make
	 * never-synced references the last thing a capped batch reaches on one deployment and the first on
	 * another.</p>
	 */
	protected List<BillingSubscriptionRefModel> findCandidates(final Instant staleBefore, final int batchSize)
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery(
				"SELECT {pk} FROM {BillingSubscriptionRef} "
						+ "WHERE ({status} IS NULL OR {status} NOT IN (?terminalStatuses)) "
						+ "AND ({status} = ?pastDue "
						+ "OR {lastSyncedAt} IS NULL "
						+ "OR {lastSyncedAt} < ?staleBefore) "
						+ "ORDER BY CASE WHEN {lastSyncedAt} IS NULL THEN 0 ELSE 1 END ASC, {lastSyncedAt} ASC");

		query.addQueryParameter("terminalStatuses", TERMINAL_STATUSES);
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
