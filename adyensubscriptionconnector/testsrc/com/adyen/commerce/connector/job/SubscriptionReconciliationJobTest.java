package com.adyen.commerce.connector.job;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.apache.commons.configuration2.Configuration;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adyen.commerce.connector.context.SubscriptionBaseStoreSelectorStrategy;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;
import com.adyen.commerce.connector.reconciliation.SubscriptionReconciliationService;

import de.hybris.platform.basecommerce.model.site.BaseSiteModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.cronjob.enums.CronJobResult;
import de.hybris.platform.cronjob.enums.CronJobStatus;
import de.hybris.platform.cronjob.model.CronJobModel;
import de.hybris.platform.site.BaseSiteService;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.cronjob.PerformResult;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.search.SearchResult;
import de.hybris.platform.servicelayer.session.SessionExecutionBody;
import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.store.BaseStoreModel;

@UnitTest
public class SubscriptionReconciliationJobTest
{
	@Mock
	private FlexibleSearchService flexibleSearchService;
	@Mock
	private SubscriptionReconciliationService reconciliationService;
	@Mock
	private ConfigurationService configurationService;
	@Mock
	private Configuration configuration;
	@Mock
	private SearchResult<BillingSubscriptionRefModel> searchResult;
	@Mock
	private BillingSubscriptionRefModel subscription;
	@Mock
	private ModelService modelService;
	@Mock
	private CronJobModel cronJob;
	@Mock
	private SessionService sessionService;
	@Mock
	private BaseSiteService baseSiteService;
	@Mock
	private AbstractOrderModel order;
	@Mock
	private BaseStoreModel store;
	@Mock
	private BaseSiteModel site;

	private SubscriptionReconciliationJob job;

	@Before
	public void setUp()
	{
		MockitoAnnotations.openMocks(this);
		job = new SubscriptionReconciliationJob();
		job.setFlexibleSearchService(flexibleSearchService);
		job.setReconciliationService(reconciliationService);
		job.setConfigurationService(configurationService);
		job.setSessionService(sessionService);
		job.setBaseSiteService(baseSiteService);
		job.setModelService(modelService);
		when(configurationService.getConfiguration()).thenReturn(configuration);
		when(configuration.getInt(SubscriptionReconciliationJob.STALE_AFTER_MINUTES, 60)).thenReturn(60);
		when(configuration.getInt(SubscriptionReconciliationJob.BATCH_SIZE, 100)).thenReturn(100);
		when(flexibleSearchService.<BillingSubscriptionRefModel>search(any(FlexibleSearchQuery.class)))
				.thenReturn(searchResult);
		when(searchResult.getResult()).thenReturn(List.of(subscription));
		when(subscription.getOrder()).thenReturn(order);
		when(order.getStore()).thenReturn(store);
		when(order.getSite()).thenReturn(site);
		when(sessionService.executeInLocalViewWithParams(anyMap(), any(SessionExecutionBody.class)))
				.thenAnswer(invocation -> invocation.getArgument(1, SessionExecutionBody.class).execute());
	}

	@Test
	public void staleOrPastDueSubscriptionIsReconciledInOneSweep() throws Exception
	{
		final PerformResult result = job.perform(cronJob);

		verify(reconciliationService).reconcile(subscription);
		verify(baseSiteService).setCurrentBaseSite(site, false);
		verify(sessionService).executeInLocalViewWithParams(
				org.mockito.ArgumentMatchers.argThat((Map<String, Object> params) -> store.equals(
						params.get(SubscriptionBaseStoreSelectorStrategy.CURRENT_SUBSCRIPTION_BASE_STORE))),
				any(SessionExecutionBody.class));
		assertEquals(CronJobResult.SUCCESS, result.getResult());
		assertEquals(CronJobStatus.FINISHED, result.getStatus());
	}

	@Test
	public void missingOriginatingStoreFailsSafelyWithoutCallingConnector() throws Exception
	{
		when(order.getStore()).thenReturn(null);

		final PerformResult result = job.perform(cronJob);

		verify(reconciliationService, never()).reconcile(subscription);
		assertEquals(CronJobResult.ERROR, result.getResult());
		assertEquals(CronJobStatus.FINISHED, result.getStatus());
	}

	/**
	 * A subscription that has ended is never re-fetched, but a past-due one always is. Asserted on the query
	 * itself because the alternative — noticing that a sweep is spending its whole batch on subscriptions
	 * that ended long ago — only surfaces as platform rate-limiting in production.
	 */
	@Test
	public void terminalSubscriptionsAreExcludedWhilePastDueStaysEligible()
	{
		job.perform(cronJob);

		final FlexibleSearchQuery query = capturedQuery();
		assertTrue("terminal statuses must be filtered out in SQL, not after the batch limit",
				query.getQuery().contains("{status} NOT IN (?terminalStatuses)"));
		assertEquals(List.of("CANCELLED", "EXPIRED", "FAILED"),
				query.getQueryParameters().get("terminalStatuses"));
		assertEquals("PAST_DUE", query.getQueryParameters().get("pastDue"));
		assertTrue("a reference whose status was never written is exactly what the sweep is for",
				query.getQuery().contains("{status} IS NULL OR"));
	}

	/**
	 * A subscription whose cancellation only takes effect at the end of the term keeps serving its customer,
	 * and both connectors normalize that to ACTIVE with cancelAtPeriodEnd — so the pending end must not
	 * become a second exclusion. Filtering on it would put the reference beyond the sweep's reach for the
	 * whole remainder of the term, which is exactly the stretch during which a dropped webhook still needs
	 * repairing.
	 */
	@Test
	public void aSubscriptionServingOutACancelledTermStaysASweepCandidate()
	{
		job.perform(cronJob);

		final FlexibleSearchQuery query = capturedQuery();
		assertFalse("the pending end of a term must not exclude a subscription that is still serving it",
				query.getQuery().contains("cancelAtPeriodEnd"));
		assertFalse("ACTIVE is what a cancellation scheduled for period end normalizes to",
				((List<?>) query.getQueryParameters().get("terminalStatuses")).contains("ACTIVE"));
	}

	/**
	 * Oracle sorts nulls last on an ascending sort while MySQL and SQL Server sort them first, so leaving it
	 * implicit would mean never-synced references head the batch on one deployment and are cut off it on
	 * another.
	 */
	@Test
	public void neverSyncedReferencesAreOrderedFirstIndependentlyOfTheDatabase()
	{
		job.perform(cronJob);

		assertTrue("null lastSyncedAt must sort first explicitly", capturedQuery().getQuery()
				.contains("ORDER BY CASE WHEN {lastSyncedAt} IS NULL THEN 0 ELSE 1 END ASC, {lastSyncedAt} ASC"));
	}

	/**
	 * The keys are read from configuration, so a divergence from project.properties is invisible until the
	 * job silently runs on its hardcoded defaults.
	 */
	@Test
	public void configurationKeysShareTheAdyenSubscriptionNamespace()
	{
		assertEquals("adyen.subscription.reconciliation.staleAfterMinutes",
				SubscriptionReconciliationJob.STALE_AFTER_MINUTES);
		assertEquals("adyen.subscription.reconciliation.batchSize", SubscriptionReconciliationJob.BATCH_SIZE);
	}

	private FlexibleSearchQuery capturedQuery()
	{
		final ArgumentCaptor<FlexibleSearchQuery> captor = ArgumentCaptor.forClass(FlexibleSearchQuery.class);
		verify(flexibleSearchService).search(captor.capture());
		return captor.getValue();
	}
}
