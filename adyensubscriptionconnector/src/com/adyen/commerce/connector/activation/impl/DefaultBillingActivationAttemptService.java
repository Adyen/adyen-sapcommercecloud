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

import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adyen.commerce.connector.activation.BillingActivationAttemptService;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.model.BillingActivationAttemptModel;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;
import com.adyen.commerce.connector.retry.BillingRetryPolicy;
import com.adyen.commerce.connector.retry.RetryVerdict;

import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.servicelayer.exceptions.ModelSavingException;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;

/**
 * Default journal, one row per {@code (order, platform)} — the same scope activation itself is
 * idempotent on, so repeating an activation updates a record rather than adding one.
 *
 * <h3>Two threads, one order</h3>
 * <p>A partial payment produces one Adyen notification per leg and Adyen redelivers on top of that, so
 * two activations of the same order genuinely overlap. The unique index decides which of them owns the
 * row; the loser takes the winner's row rather than failing, because both are doing the same work for
 * the same order and the count of attempts should reflect that work once. The one thing that must not
 * happen is the loser's failure erasing the winner's success, which {@link #failed} refuses to do.</p>
 */
public class DefaultBillingActivationAttemptService implements BillingActivationAttemptService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultBillingActivationAttemptService.class);

	/** Keeps one pathological remote error body from filling the column and the log with it. */
	protected static final int MAX_ERROR_LENGTH = 8000;

	private ModelService modelService;
	private FlexibleSearchService flexibleSearchService;
	private BillingRetryPolicy retryPolicy;
	private Clock clock = Clock.systemUTC();

	@Override
	public BillingActivationAttemptModel begin(final OrderModel order, final BillingPlatform platform,
			final String productCode, final String idempotencyKey)
	{
		final BillingActivationAttemptModel attempt = findOrCreate(order, platform);
		if (STATUS_SUCCEEDED.equals(attempt.getStatus()))
		{
			// Already activated, and more than one trigger reaches this order: the place-order path announces
			// an ordinary authorization, the 3DS return announces its own, and Adyen redelivers notifications
			// on top of both. Reopening the record would walk a finished activation back to PENDING and spend
			// one of the retries the policy is holding for a failure that has not happened. The caller runs on
			// regardless; activateSubscription finds the existing subscription reference and returns it, so
			// nothing is charged twice and succeeded() simply restamps what is already there.
			return attempt;
		}
		attempt.setProductCode(productCode);
		attempt.setIdempotencyKey(idempotencyKey);
		attempt.setAttemptCount(Integer.valueOf(attemptCount(attempt) + 1));
		attempt.setStatus(STATUS_PENDING);
		final Date now = now();
		if (attempt.getFirstAttemptAt() == null)
		{
			attempt.setFirstAttemptAt(now);
		}
		attempt.setLastAttemptAt(now);
		// Out of the retry job's queue while the attempt is in flight, so a long call cannot be picked up a
		// second time by the next run of the job.
		attempt.setNextAttemptAt(null);
		return save(attempt, order, platform);
	}

	@Override
	public void succeeded(final BillingActivationAttemptModel attempt, final BillingSubscriptionRefModel subscriptionRef)
	{
		attempt.setStatus(STATUS_SUCCEEDED);
		attempt.setSubscriptionRef(subscriptionRef);
		attempt.setNextAttemptAt(null);
		// The error and the dead-letter stamp are cleared rather than kept: an operator scanning for
		// unresolved trouble should not have to read the status column to tell that this row is resolved.
		attempt.setLastError(null);
		attempt.setDeadLetteredAt(null);
		modelService.save(attempt);
	}

	@Override
	public RetryVerdict failed(final BillingActivationAttemptModel attempt, final Throwable failure)
	{
		if (hasSucceededMeanwhile(attempt))
		{
			LOG.info("Activation for order '{}' on platform {} failed here, but another attempt has already "
					+ "succeeded; leaving the record alone.", orderCode(attempt), attempt.getPlatform(), failure);
			return RetryVerdict.giveUp("superseded by a successful attempt");
		}

		final RetryVerdict verdict = retryPolicy.decide(failure, attemptCount(attempt), clock.instant());
		attempt.setLastError(describe(failure));
		if (verdict.retry())
		{
			attempt.setStatus(STATUS_FAILED);
			attempt.setNextAttemptAt(Date.from(verdict.nextAttemptAt()));
			LOG.warn("Activation for order '{}' on platform {} failed: {}. Next attempt at {}.", orderCode(attempt),
					attempt.getPlatform(), verdict.reason(), verdict.nextAttemptAt(), failure);
		}
		else
		{
			attempt.setStatus(STATUS_DEAD_LETTER);
			attempt.setNextAttemptAt(null);
			attempt.setDeadLetteredAt(now());
			// The one line in this whole path worth alerting on: the shopper has been charged and there is no
			// subscription, and nothing else is going to try again.
			LOG.error("DEAD LETTER: giving up on activating a {} subscription for order '{}' after {} attempt(s) — {}. "
					+ "The shopper was charged and has no subscription; this needs an operator.", attempt.getPlatform(),
					orderCode(attempt), attemptCount(attempt), verdict.reason(), failure);
		}
		modelService.save(attempt);
		return verdict;
	}

	@Override
	public void abandon(final BillingActivationAttemptModel attempt, final String reason)
	{
		attempt.setStatus(STATUS_DEAD_LETTER);
		attempt.setNextAttemptAt(null);
		attempt.setDeadLetteredAt(now());
		attempt.setLastError(reason);
		LOG.error("DEAD LETTER: abandoning the activation of a {} subscription for order '{}' — {}. The shopper was "
				+ "charged and has no subscription; this needs an operator.", attempt.getPlatform(), orderCode(attempt),
				reason);
		modelService.save(attempt);
	}

	@Override
	public void notApplicable(final OrderModel order, final BillingPlatform platform, final String reason)
	{
		final Optional<BillingActivationAttemptModel> existing = find(order, platform);
		if (existing.isEmpty())
		{
			return;
		}
		final BillingActivationAttemptModel attempt = existing.get();
		if (STATUS_SUCCEEDED.equals(attempt.getStatus()))
		{
			// It really did activate something once. Whatever the rule says now, that row is not ours to
			// rewrite - the subscription it points at exists on the platform.
			return;
		}
		attempt.setStatus(STATUS_NOT_APPLICABLE);
		attempt.setNextAttemptAt(null);
		attempt.setLastError(reason);
		LOG.info("Closing the activation record for order '{}' on {} as not applicable — {}", orderCode(attempt),
				platform, reason);
		modelService.save(attempt);
	}

	@Override
	public List<BillingActivationAttemptModel> findDue(final Instant now, final Instant stalePendingBefore,
			final int limit)
	{
		if (limit <= 0)
		{
			return List.of();
		}
		// Ordered by lastAttemptAt rather than nextAttemptAt: the pending half of the union has no due date,
		// and where nulls sort is a per-database answer. Oldest untouched first is meaningful for both halves.
		final FlexibleSearchQuery query = new FlexibleSearchQuery("SELECT {pk} FROM {BillingActivationAttempt} "
				+ "WHERE ({status} = ?failed AND {nextAttemptAt} IS NOT NULL AND {nextAttemptAt} <= ?now) "
				+ "OR ({status} = ?pending AND {lastAttemptAt} IS NOT NULL AND {lastAttemptAt} <= ?stalePendingBefore) "
				+ "ORDER BY {lastAttemptAt} ASC");
		query.addQueryParameter("failed", STATUS_FAILED);
		query.addQueryParameter("pending", STATUS_PENDING);
		query.addQueryParameter("now", Date.from(now));
		query.addQueryParameter("stalePendingBefore", Date.from(stalePendingBefore));
		query.setCount(limit);
		return flexibleSearchService.<BillingActivationAttemptModel> search(query).getResult();
	}

	/**
	 * Reads the row back before writing a failure to it, because the model in hand may have been overtaken
	 * by a concurrent attempt that finished first. Only a persisted {@code SUCCEEDED} counts: an unsaved
	 * one is this thread's own optimism.
	 */
	protected boolean hasSucceededMeanwhile(final BillingActivationAttemptModel attempt)
	{
		try
		{
			modelService.refresh(attempt);
		}
		catch (final RuntimeException e)
		{
			// Refreshing is an optimisation, not the point. If it fails, record the failure anyway — losing
			// the failure entirely is worse than the small chance of overwriting a concurrent success.
			LOG.debug("Could not refresh activation attempt for order '{}' before recording a failure",
					orderCode(attempt), e);
			return false;
		}
		return STATUS_SUCCEEDED.equals(attempt.getStatus());
	}

	protected BillingActivationAttemptModel findOrCreate(final OrderModel order, final BillingPlatform platform)
	{
		return find(order, platform).orElseGet(() -> {
			final BillingActivationAttemptModel created = modelService.create(BillingActivationAttemptModel.class);
			created.setOrder(order);
			created.setPlatform(platform);
			return created;
		});
	}

	/**
	 * Saves, treating a rejection by the unique index as "another thread got here first" only when the row
	 * really is there afterwards — the same rule the webhook dispatcher applies when claiming an event id.
	 * Any other save failure is rethrown: a journal that quietly fails to record is worse than none.
	 *
	 * @return the row to carry on with, which on a lost race is the winner's rather than the one just built
	 */
	protected BillingActivationAttemptModel save(final BillingActivationAttemptModel attempt, final OrderModel order,
			final BillingPlatform platform)
	{
		try
		{
			modelService.save(attempt);
			return attempt;
		}
		catch (final ModelSavingException e)
		{
			final Optional<BillingActivationAttemptModel> existing = find(order, platform);
			if (existing.isEmpty())
			{
				throw e;
			}
			LOG.info("Concurrent activation of order '{}' on platform {} created the attempt record first; "
					+ "continuing on it.", order.getCode(), platform, e);
			// The winner's row is returned as it stands, deliberately without re-bumping the counter onto it.
			// The winner already counted this activation of this order and both threads are about to do the
			// same work; counting it twice would spend the retry budget at twice the intended rate.
			return existing.get();
		}
	}

	protected Optional<BillingActivationAttemptModel> find(final OrderModel order, final BillingPlatform platform)
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery("SELECT {pk} FROM {BillingActivationAttempt} "
				+ "WHERE {order} = ?order AND {platform} = ?platform");
		query.addQueryParameter("order", order);
		query.addQueryParameter("platform", platform);
		final List<BillingActivationAttemptModel> result = flexibleSearchService
				.<BillingActivationAttemptModel> search(query).getResult();
		return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
	}

	/**
	 * The exception and its causes, which is what tells an operator whether the platform refused the request
	 * or never heard it. The stack trace is left to the log, where it is already written in full.
	 */
	protected static String describe(final Throwable failure)
	{
		if (failure == null)
		{
			return null;
		}
		final StringBuilder description = new StringBuilder();
		Throwable current = failure;
		// Bounded because a cause chain can be circular, and this runs on a path that is already going badly.
		for (int depth = 0; current != null && depth < 10; depth++)
		{
			if (depth > 0)
			{
				description.append("\ncaused by: ");
			}
			description.append(current.getClass().getName()).append(": ").append(current.getMessage());
			current = current.getCause() == current ? null : current.getCause();
		}
		return description.length() > MAX_ERROR_LENGTH ? description.substring(0, MAX_ERROR_LENGTH) : description.toString();
	}

	protected static int attemptCount(final BillingActivationAttemptModel attempt)
	{
		final Integer count = attempt.getAttemptCount();
		return count == null ? 0 : count.intValue();
	}

	private static String orderCode(final BillingActivationAttemptModel attempt)
	{
		return attempt.getOrder() == null ? null : attempt.getOrder().getCode();
	}

	private Date now()
	{
		return Date.from(clock.instant());
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}

	public void setRetryPolicy(final BillingRetryPolicy retryPolicy)
	{
		this.retryPolicy = retryPolicy;
	}

	public void setClock(final Clock clock)
	{
		this.clock = clock;
	}
}
