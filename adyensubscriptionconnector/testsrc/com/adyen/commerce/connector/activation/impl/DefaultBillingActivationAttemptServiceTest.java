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
package com.adyen.commerce.connector.activation.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adyen.commerce.connector.activation.BillingActivationAttemptService;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.ConnectorNotConfiguredException;
import com.adyen.commerce.connector.exception.RetryableBillingException;
import com.adyen.commerce.connector.model.BillingActivationAttemptModel;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;
import com.adyen.commerce.connector.retry.impl.DefaultBillingRetryPolicy;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.servicelayer.exceptions.ModelSavingException;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.search.SearchResult;

/**
 * Unit test for {@link DefaultBillingActivationAttemptService} — the journal that turns a swallowed
 * failure into something retryable and, failing that, into something an operator can find.
 *
 * <p>The models are stateful mocks and the search service is backed by a list, because the claims worth
 * testing are about a record carrying state from one call to the next. A plain mock returning null from
 * every getter would pass the assertions without the code doing any work.</p>
 */
@UnitTest
public class DefaultBillingActivationAttemptServiceTest
{
	private static final Instant NOW = Instant.parse("2026-08-20T12:00:00Z");
	private static final int MAX_ATTEMPTS = 3;

	@Mock
	private ModelService modelService;
	@Mock
	private FlexibleSearchService flexibleSearchService;
	@Mock
	private OrderModel order;

	private DefaultBillingActivationAttemptService attemptService;

	/** Stands in for the table and its unique (order, platform) index. */
	private final List<BillingActivationAttemptModel> stored = new ArrayList<>();

	@Before
	public void setUp()
	{
		MockitoAnnotations.openMocks(this);

		final DefaultBillingRetryPolicy retryPolicy = new DefaultBillingRetryPolicy();
		retryPolicy.setMaxAttempts(MAX_ATTEMPTS);
		retryPolicy.setInitialBackoffSeconds(60L);

		attemptService = new DefaultBillingActivationAttemptService();
		attemptService.setModelService(modelService);
		attemptService.setFlexibleSearchService(flexibleSearchService);
		attemptService.setRetryPolicy(retryPolicy);
		attemptService.setClock(Clock.fixed(NOW, ZoneOffset.UTC));

		when(order.getCode()).thenReturn("order-1");
		when(modelService.create(BillingActivationAttemptModel.class)).thenAnswer(i -> statefulAttempt());
		doAnswer(i -> {
			final Object saved = i.getArgument(0);
			if (saved instanceof BillingActivationAttemptModel attempt && !stored.contains(attempt))
			{
				stored.add(attempt);
			}
			return null;
		}).when(modelService).save(any());
		when(flexibleSearchService.search(any(FlexibleSearchQuery.class))).thenAnswer(i -> searchResult(stored));
	}

	@Test
	public void opensARecordBeforeThePlatformIsEverCalled()
	{
		final BillingActivationAttemptModel attempt = begin();

		assertEquals(BillingActivationAttemptService.STATUS_PENDING, attempt.getStatus());
		assertEquals(Integer.valueOf(1), attempt.getAttemptCount());
		assertEquals("order-1", attempt.getIdempotencyKey());
		assertEquals("sub-product", attempt.getProductCode());
		assertEquals(Date.from(NOW), attempt.getFirstAttemptAt());
		assertEquals(Date.from(NOW), attempt.getLastAttemptAt());
		// Out of the retry job's queue while it is in flight.
		assertNull(attempt.getNextAttemptAt());
		verify(modelService).save(attempt);
	}

	@Test
	public void countsARepeatedAttemptOnTheSameRecord()
	{
		final BillingActivationAttemptModel first = begin();
		final BillingActivationAttemptModel second = begin();

		assertSame(first, second);
		assertEquals(Integer.valueOf(2), second.getAttemptCount());
		assertEquals(1, stored.size());
	}

	/**
	 * Two Adyen notification legs for one order genuinely overlap. The index picks a winner; the loser
	 * carries on with the winner's row rather than failing or starting a second count.
	 */
	@Test
	public void carriesOnWithTheWinnersRecordAfterALostRace()
	{
		final BillingActivationAttemptModel winner = statefulAttempt();
		winner.setAttemptCount(Integer.valueOf(1));
		winner.setStatus(BillingActivationAttemptService.STATUS_PENDING);
		// Invisible until our own insert is rejected — which is exactly the order a lost race happens in.
		final java.util.concurrent.atomic.AtomicBoolean winnerVisible = new java.util.concurrent.atomic.AtomicBoolean();
		when(flexibleSearchService.search(any(FlexibleSearchQuery.class)))
				.thenAnswer(i -> searchResult(winnerVisible.get() ? List.of(winner) : List.of()));
		doAnswer(i -> {
			winnerVisible.set(true);
			throw new ModelSavingException("unique index violated");
		}).when(modelService).save(any());

		final BillingActivationAttemptModel result = begin();

		assertSame(winner, result);
		// Not re-counted: the winner already counted this activation of this order, and counting it twice
		// would spend the retry budget at twice the intended rate.
		assertEquals(Integer.valueOf(1), result.getAttemptCount());
	}

	@Test
	public void rethrowsASaveFailureThatIsNotTheRace()
	{
		doThrow(new ModelSavingException("the database is gone")).when(modelService).save(any());

		assertThrows(ModelSavingException.class, this::begin);
	}

	@Test
	public void closesTheRecordOnSuccess()
	{
		final BillingActivationAttemptModel attempt = begin();
		attempt.setLastError("a previous attempt failed");
		final BillingSubscriptionRefModel ref = mock(BillingSubscriptionRefModel.class);

		attemptService.succeeded(attempt, ref);

		assertEquals(BillingActivationAttemptService.STATUS_SUCCEEDED, attempt.getStatus());
		assertSame(ref, attempt.getSubscriptionRef());
		assertNull(attempt.getNextAttemptAt());
		// Cleared so an operator scanning for trouble does not have to read the status to rule this row out.
		assertNull(attempt.getLastError());
	}

	@Test
	public void schedulesARetryForATransientFailure()
	{
		final BillingActivationAttemptModel attempt = begin();

		attemptService.failed(attempt, new RetryableBillingException("Chargebee is down"));

		assertEquals(BillingActivationAttemptService.STATUS_FAILED, attempt.getStatus());
		assertEquals(Date.from(NOW.plusSeconds(60L)), attempt.getNextAttemptAt());
		assertTrue(attempt.getLastError().contains("Chargebee is down"));
		assertNull(attempt.getDeadLetteredAt());
	}

	@Test
	public void deadLettersATerminalFailureAtOnce()
	{
		final BillingActivationAttemptModel attempt = begin();

		attemptService.failed(attempt, new ConnectorNotConfiguredException("no connector for CHARGEBEE"));

		assertEquals(BillingActivationAttemptService.STATUS_DEAD_LETTER, attempt.getStatus());
		assertEquals(Date.from(NOW), attempt.getDeadLetteredAt());
		assertNull(attempt.getNextAttemptAt());
	}

	@Test
	public void deadLettersOnceTheAttemptsAreExhausted()
	{
		BillingActivationAttemptModel attempt = null;
		for (int i = 0; i < MAX_ATTEMPTS; i++)
		{
			attempt = begin();
			attemptService.failed(attempt, new RetryableBillingException("still down"));
		}

		assertEquals(BillingActivationAttemptService.STATUS_DEAD_LETTER, attempt.getStatus());
		assertEquals(Integer.valueOf(MAX_ATTEMPTS), attempt.getAttemptCount());
	}

	/**
	 * A concurrent attempt that already succeeded must not be talked out of it by the loser's failure.
	 */
	@Test
	public void refusesToOverwriteASuccessThatLandedMeanwhile()
	{
		final BillingActivationAttemptModel attempt = begin();
		attempt.setStatus(BillingActivationAttemptService.STATUS_SUCCEEDED);

		attemptService.failed(attempt, new RetryableBillingException("too late"));

		assertEquals(BillingActivationAttemptService.STATUS_SUCCEEDED, attempt.getStatus());
		assertNull(attempt.getNextAttemptAt());
	}

	@Test
	public void abandonsAnUnactionableAttemptWithItsReason()
	{
		final BillingActivationAttemptModel attempt = begin();

		attemptService.abandon(attempt, "the order no longer resolves to a subscription");

		assertEquals(BillingActivationAttemptService.STATUS_DEAD_LETTER, attempt.getStatus());
		assertEquals("the order no longer resolves to a subscription", attempt.getLastError());
		assertEquals(Date.from(NOW), attempt.getDeadLetteredAt());
		assertNull(attempt.getNextAttemptAt());
	}

	@Test
	public void asksForNothingWhenTheBatchLimitIsZero()
	{
		assertTrue(attemptService.findDue(NOW, NOW.minusSeconds(1800L), 0).isEmpty());
		verify(flexibleSearchService, never()).search(any(FlexibleSearchQuery.class));
	}

	/**
	 * A remote error body can be arbitrarily long; the column and the reader both have limits.
	 */
	@Test
	public void truncatesARunawayErrorDescription()
	{
		final String huge = "x".repeat(DefaultBillingActivationAttemptService.MAX_ERROR_LENGTH * 2);

		final String described = DefaultBillingActivationAttemptService.describe(new IllegalStateException(huge));

		assertEquals(DefaultBillingActivationAttemptService.MAX_ERROR_LENGTH, described.length());
	}

	@Test
	public void describesTheCauseChain()
	{
		final Exception failure = new IllegalStateException("outer", new IllegalArgumentException("inner"));

		final String described = DefaultBillingActivationAttemptService.describe(failure);

		assertTrue(described.contains("outer"));
		assertTrue(described.contains("inner"));
	}

	// ---------------------------------------------------------------- helpers

	private BillingActivationAttemptModel begin()
	{
		return attemptService.begin(order, BillingPlatform.CHARGEBEE, "sub-product", "order-1");
	}

	private static SearchResult<BillingActivationAttemptModel> searchResult(
			final List<BillingActivationAttemptModel> result)
	{
		@SuppressWarnings("unchecked")
		final SearchResult<BillingActivationAttemptModel> searchResult = mock(SearchResult.class);
		when(searchResult.getResult()).thenReturn(new ArrayList<>(result));
		return searchResult;
	}

	/**
	 * Written out longhand rather than reflected over, matching the webhook dispatcher's test: the mock is
	 * infrastructure every test here leans on, and infrastructure that is clever is infrastructure nobody
	 * can debug when it breaks.
	 */
	private static BillingActivationAttemptModel statefulAttempt()
	{
		final Map<String, Object> state = new HashMap<>();
		final BillingActivationAttemptModel attempt = mock(BillingActivationAttemptModel.class);
		doAnswer(i -> { state.put("status", i.getArgument(0)); return null; }).when(attempt).setStatus(any());
		when(attempt.getStatus()).thenAnswer(i -> state.get("status"));
		doAnswer(i -> { state.put("attempts", i.getArgument(0)); return null; }).when(attempt).setAttemptCount(any());
		when(attempt.getAttemptCount()).thenAnswer(i -> state.get("attempts"));
		doAnswer(i -> { state.put("key", i.getArgument(0)); return null; }).when(attempt).setIdempotencyKey(any());
		when(attempt.getIdempotencyKey()).thenAnswer(i -> state.get("key"));
		doAnswer(i -> { state.put("product", i.getArgument(0)); return null; }).when(attempt).setProductCode(any());
		when(attempt.getProductCode()).thenAnswer(i -> state.get("product"));
		doAnswer(i -> { state.put("lastError", i.getArgument(0)); return null; }).when(attempt).setLastError(any());
		when(attempt.getLastError()).thenAnswer(i -> state.get("lastError"));
		doAnswer(i -> { state.put("firstAt", i.getArgument(0)); return null; }).when(attempt).setFirstAttemptAt(any());
		when(attempt.getFirstAttemptAt()).thenAnswer(i -> state.get("firstAt"));
		doAnswer(i -> { state.put("lastAt", i.getArgument(0)); return null; }).when(attempt).setLastAttemptAt(any());
		when(attempt.getLastAttemptAt()).thenAnswer(i -> state.get("lastAt"));
		doAnswer(i -> { state.put("nextAt", i.getArgument(0)); return null; }).when(attempt).setNextAttemptAt(any());
		when(attempt.getNextAttemptAt()).thenAnswer(i -> state.get("nextAt"));
		doAnswer(i -> { state.put("dlqAt", i.getArgument(0)); return null; }).when(attempt).setDeadLetteredAt(any());
		when(attempt.getDeadLetteredAt()).thenAnswer(i -> state.get("dlqAt"));
		doAnswer(i -> { state.put("ref", i.getArgument(0)); return null; }).when(attempt).setSubscriptionRef(any());
		when(attempt.getSubscriptionRef()).thenAnswer(i -> state.get("ref"));
		return attempt;
	}
}
