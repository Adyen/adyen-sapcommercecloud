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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adyen.commerce.connector.model.BillingActivationAttemptModel;
import com.adyen.commerce.connector.model.BillingWebhookEventApplicationModel;
import com.adyen.commerce.connector.model.BillingWebhookEventModel;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.cronjob.enums.CronJobResult;
import de.hybris.platform.cronjob.model.CronJobModel;
import de.hybris.platform.servicelayer.cronjob.PerformResult;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.search.SearchResult;

/**
 * Unit test for {@link SubscriptionBillingRetentionJob}.
 *
 * <p>The windows themselves are expressed in the queries, which a unit test cannot execute. What it can
 * pin down is the part that goes wrong quietly: the order of removal, and the two cut-off dates the job
 * hands the database being genuinely different rather than one value used twice.</p>
 */
@UnitTest
public class SubscriptionBillingRetentionJobTest
{
	private static final Instant NOW = Instant.parse("2026-08-26T12:00:00Z");

	@Mock
	private FlexibleSearchService flexibleSearchService;
	@Mock
	private ModelService modelService;
	@Mock
	private CronJobModel cronJob;

	private SubscriptionBillingRetentionJob job;
	private final List<FlexibleSearchQuery> executed = new ArrayList<>();

	@Before
	public void setUp()
	{
		MockitoAnnotations.openMocks(this);

		job = new SubscriptionBillingRetentionJob()
		{
			@Override
			protected boolean isAbortRequested(final CronJobModel ignored)
			{
				// The platform's abort check needs a booted cron engine; the ordering tests are not about it.
				return false;
			}
		};
		job.setFlexibleSearchService(flexibleSearchService);
		job.setModelService(modelService);
		job.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
		job.setSettledAfterDays(30);
		job.setTroubledAfterDays(180);
		job.setBatchSize(500);

		final SearchResult<Object> empty = emptyResult();
		when(flexibleSearchService.search(any(FlexibleSearchQuery.class))).thenAnswer(invocation -> {
			executed.add(invocation.getArgument(0));
			return empty;
		});
	}

	@Test
	public void removesNothingAndSaysNothingWhenBothJournalsAreClean()
	{
		final PerformResult result = job.perform(cronJob);

		assertEquals(CronJobResult.SUCCESS, result.getResult());
		verify(modelService, never()).remove(any());
	}

	/**
	 * The application's reference to its delivery is a plain attribute, not a relation, so nothing cascades.
	 * Removing the parent first would leave rows pointing at a deleted item through a mandatory field.
	 */
	@Test
	public void removesAnApplicationBeforeTheDeliveryItBelongsTo()
	{
		final BillingWebhookEventModel event = mock(BillingWebhookEventModel.class);
		final BillingWebhookEventApplicationModel application = mock(BillingWebhookEventApplicationModel.class);
		// Built before the chain, never inside it: stubbing a mock while another stubbing is still open is
		// what UnfinishedStubbingException is.
		final SearchResult<Object> events = result(List.of(event));
		final SearchResult<Object> applications = result(List.of(application));
		final SearchResult<Object> empty = emptyResult();
		when(flexibleSearchService.search(any(FlexibleSearchQuery.class)))
				.thenReturn(events)
				.thenReturn(applications)
				.thenReturn(empty);

		job.perform(cronJob);

		final InOrder order = inOrder(modelService);
		order.verify(modelService).remove(application);
		order.verify(modelService).remove(event);
	}

	/**
	 * A quiet success and a dead letter must not be measured against the same date, or one of the two
	 * windows is decoration.
	 */
	@Test
	public void measuresTheTroubledRowsAgainstAnOlderCutOffThanTheSettledOnes()
	{
		job.perform(cronJob);

		final FlexibleSearchQuery webhookQuery = executed.get(0);
		final Date settledBefore = (Date) webhookQuery.getQueryParameters().get("settledBefore");
		final Date troubledBefore = (Date) webhookQuery.getQueryParameters().get("troubledBefore");

		assertTrue("a troubled row must survive longer than a settled one",
				troubledBefore.before(settledBefore));
		assertEquals(Date.from(NOW.minusSeconds(30L * 24 * 3600)), settledBefore);
		assertEquals(Date.from(NOW.minusSeconds(180L * 24 * 3600)), troubledBefore);
	}

	/**
	 * Both journals are swept in one run: an activation attempt is not reachable from a webhook delivery,
	 * so a sweep that only walked the deliveries would leave the other table growing untouched.
	 */
	@Test
	public void sweepsTheActivationJournalAsWellAsTheDeliveries()
	{
		final BillingActivationAttemptModel attempt = mock(BillingActivationAttemptModel.class);
		final SearchResult<Object> noDeliveries = emptyResult();
		final SearchResult<Object> attempts = result(List.of(attempt));
		when(flexibleSearchService.search(any(FlexibleSearchQuery.class)))
				.thenReturn(noDeliveries)
				.thenReturn(attempts);

		job.perform(cronJob);

		verify(modelService).remove(attempt);
	}

	private static SearchResult<Object> emptyResult()
	{
		return result(List.of());
	}

	/**
	 * Typed as Object because {@code search} is generic on its return: the stubbing infers one type for the
	 * whole chain, and the job asks it for three different ones.
	 */
	@SuppressWarnings("unchecked")
	private static SearchResult<Object> result(final List<?> items)
	{
		final SearchResult<Object> searchResult = mock(SearchResult.class);
		when(searchResult.getResult()).thenReturn((List<Object>) items);
		return searchResult;
	}
}
