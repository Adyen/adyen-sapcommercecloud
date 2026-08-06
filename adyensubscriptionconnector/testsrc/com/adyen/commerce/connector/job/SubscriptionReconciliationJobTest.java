package com.adyen.commerce.connector.job;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.apache.commons.configuration2.Configuration;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;
import com.adyen.commerce.connector.reconciliation.SubscriptionReconciliationService;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.cronjob.enums.CronJobResult;
import de.hybris.platform.cronjob.enums.CronJobStatus;
import de.hybris.platform.cronjob.model.CronJobModel;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.cronjob.PerformResult;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.search.SearchResult;

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

	private SubscriptionReconciliationJob job;

	@Before
	public void setUp()
	{
		MockitoAnnotations.openMocks(this);
		job = new SubscriptionReconciliationJob();
		job.setFlexibleSearchService(flexibleSearchService);
		job.setReconciliationService(reconciliationService);
		job.setConfigurationService(configurationService);
		job.setModelService(modelService);
		when(configurationService.getConfiguration()).thenReturn(configuration);
		when(configuration.getInt(SubscriptionReconciliationJob.STALE_AFTER_MINUTES, 60)).thenReturn(60);
		when(configuration.getInt(SubscriptionReconciliationJob.BATCH_SIZE, 100)).thenReturn(100);
		when(flexibleSearchService.<BillingSubscriptionRefModel>search(any(FlexibleSearchQuery.class)))
				.thenReturn(searchResult);
		when(searchResult.getResult()).thenReturn(List.of(subscription));
	}

	@Test
	public void staleOrPastDueSubscriptionIsReconciledInOneSweep() throws Exception
	{
		final PerformResult result = job.perform(cronJob);

		verify(reconciliationService).reconcile(subscription);
		assertEquals(CronJobResult.SUCCESS, result.getResult());
		assertEquals(CronJobStatus.FINISHED, result.getStatus());
	}
}
