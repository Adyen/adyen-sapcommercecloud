/*
 *                        ######
 *                        ######
 *  ############    ####( ######  #####. ######  ############   ############
 *  #############  #####( ######  #####. ######  #####  ######  #####  ######
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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adyen.commerce.connector.activation.SubscriptionOrderCancellationService;
import com.adyen.commerce.connector.context.SubscriptionBaseStoreSelectorStrategy;
import com.adyen.commerce.connector.dto.CancelReason;
import com.adyen.commerce.connector.dto.NormalizedSubscriptionStatus;
import com.adyen.commerce.connector.dto.SubscriptionCancellation;
import com.adyen.commerce.connector.log.ConnectorLogContext;
import com.adyen.commerce.connector.log.ConnectorLogEvent;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;
import com.adyen.commerce.connector.service.SubscriptionBillingService;

import de.hybris.platform.basecommerce.model.site.BaseSiteModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.session.SessionExecutionBody;
import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.site.BaseSiteService;
import de.hybris.platform.store.BaseStoreModel;

/**
 * Default implementation. See {@link SubscriptionOrderCancellationService} for why this exists.
 *
 * <p>Cancellation is always at the period end: neither adapter sends a refund or credit instruction with
 * a cancellation, and on Recurly immediate cancellation is a <em>terminate</em>, a different API verb with
 * its own settlement rules. Whether the shopper is owed money back is a question about the order, answered
 * by whoever refunds it.</p>
 *
 * <p>Nothing escapes this class: the caller is in the middle of cancelling an order, and a billing
 * platform that is down, misconfigured or slow must not turn that into a failed cancellation. A
 * cancellation lost that way is not retried — there is no journal behind this path — so the log line and
 * the reconciliation sweep's report of a still-active subscription are the only record.</p>
 */
public class DefaultSubscriptionOrderCancellationService implements SubscriptionOrderCancellationService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultSubscriptionOrderCancellationService.class);

	private static final String EVENT_CANCELLATION = "subscription_order_cancellation";

	/**
	 * Statuses in which there is nothing left to stop. Enumerated rather than derived, so a status added
	 * to the normalized vocabulary has to be classified here on purpose. {@code UNKNOWN} is deliberately
	 * absent: it means the last platform read could not be interpreted, not that the subscription ended,
	 * and skipping on it would leave a live subscription running behind a cancelled order.
	 */
	private static final Set<String> ALREADY_OVER = Set.of(
			NormalizedSubscriptionStatus.CANCELLED.name(),
			NormalizedSubscriptionStatus.EXPIRED.name(),
			NormalizedSubscriptionStatus.FAILED.name());

	private SubscriptionBillingService subscriptionBillingService;
	private FlexibleSearchService flexibleSearchService;
	private SessionService sessionService;
	private BaseSiteService baseSiteService;

	@Override
	public void cancelSubscriptionFor(final OrderModel order)
	{
		// The whole body is inside the guard: reading the order or its store can fail too, and this method
		// must not be able to throw at all.
		try
		{
			doCancelFor(order);
		}
		catch (final RuntimeException e)
		{
			LOG.error("Unexpected failure while cancelling the subscription of a cancelled order. The order "
					+ "cancellation stands.", e);
		}
	}

	protected void doCancelFor(final OrderModel order)
	{
		if (order == null)
		{
			return;
		}

		final List<BillingSubscriptionRefModel> refs = findSubscriptions(order);
		if (refs.isEmpty())
		{
			// Not logged: most cancelled orders never carried a subscription, and a line per cancellation
			// would drown the ones that mean something.
			return;
		}

		// Uniqueness is on (order, platform), so an order whose store was switched between billing
		// platforms carries one reference per platform and every one of them has to be stopped.
		for (final BillingSubscriptionRefModel ref : refs)
		{
			cancelOne(order, ref);
		}
	}

	protected void cancelOne(final OrderModel order, final BillingSubscriptionRefModel ref)
	{
		final long startedAt = System.nanoTime();
		try (ConnectorLogContext correlation = ConnectorLogContext.correlate(order.getCode()))
		{
			final String skipReason = reasonToLeaveAlone(ref);
			if (skipReason != null)
			{
				ConnectorLogEvent.of(EVENT_CANCELLATION)
						.platform(ref.getPlatform())
						.field("order_code", order.getCode())
						.field("subscription_id", ref.getExternalSubscriptionId())
						.outcome(ConnectorLogEvent.OUTCOME_IGNORED)
						.reason(skipReason)
						.durationSince(startedAt)
						.info(LOG);
				return;
			}

			try
			{
				cancelInStoreContext(order, ref);
				ConnectorLogEvent.of(EVENT_CANCELLATION)
						.platform(ref.getPlatform())
						.field("order_code", order.getCode())
						.field("subscription_id", ref.getExternalSubscriptionId())
						.success(startedAt)
						.info(LOG);
			}
			catch (final RuntimeException e)
			{
				// Loud: the order is cancelled and the shopper is still being billed, and with no journal
				// behind this path the log line is the only record.
				ConnectorLogEvent.of(EVENT_CANCELLATION)
						.platform(ref.getPlatform())
						.field("order_code", order.getCode())
						.field("subscription_id", ref.getExternalSubscriptionId())
						.field("exception_class", e.getClass().getName())
						.failure(startedAt, null)
						.error(LOG);
				LOG.error("Order '{}' was cancelled but its subscription '{}' on platform {} could not be stopped; "
						+ "the shopper will go on being billed until someone stops it by hand.", order.getCode(),
						ref.getExternalSubscriptionId(), ref.getPlatform(), e);
			}
		}
	}

	/**
	 * Why this reference should be left alone, or {@code null} to go ahead. Both answers are idempotency:
	 * an order can be announced cancelled more than once, and the Chargebee adapter passes no idempotency
	 * key, so a second cancellation would be a real API call.
	 */
	protected String reasonToLeaveAlone(final BillingSubscriptionRefModel ref)
	{
		if (Boolean.TRUE.equals(ref.getCancelAtPeriodEnd()))
		{
			return "already set to stop at the end of the period";
		}
		if (ref.getStatus() != null && ALREADY_OVER.contains(ref.getStatus()))
		{
			return "subscription is already over";
		}
		return null;
	}

	/**
	 * Runs the cancellation with the order's own store in context. The connectors read their API key, site
	 * and gateway account from the base store in the <em>session</em>, and this runs on an event listener's
	 * thread carrying whichever store the publishing request left there, or none — so without this the
	 * cancellation could reach another store's billing account and name a subscription that exists there.
	 */
	protected void cancelInStoreContext(final OrderModel order, final BillingSubscriptionRefModel ref)
	{
		final BaseStoreModel store = order.getStore();
		if (store == null)
		{
			throw new IllegalStateException("Order '" + order.getCode() + "' has no base store; the connector's "
					+ "credentials cannot be selected safely");
		}

		final Map<String, Object> sessionParameters = Collections.singletonMap(
				SubscriptionBaseStoreSelectorStrategy.CURRENT_SUBSCRIPTION_BASE_STORE, store);
		sessionService.executeInLocalViewWithParams(sessionParameters, new SessionExecutionBody()
		{
			@Override
			public void executeWithoutResult()
			{
				final BaseSiteModel site = order.getSite();
				if (site != null)
				{
					// false: catalog versions are not needed to read a store's connector credentials, and
					// activating them is the expensive half of the call.
					baseSiteService.setCurrentBaseSite(site, false);
				}
				try
				{
					subscriptionBillingService.cancel(ref,
							SubscriptionCancellation.endOfPeriod(CancelReason.OTHER));
				}
				catch (final Exception e)
				{
					// SessionExecutionBody cannot throw a checked exception; the caller's catch turns
					// this into a log line instead of a failed order cancellation.
					throw new IllegalStateException(e);
				}
			}
		});
	}

	/** Every subscription started by this order, ordered by platform so a run over several is repeatable. */
	protected List<BillingSubscriptionRefModel> findSubscriptions(final OrderModel order)
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery(
				"SELECT {pk} FROM {BillingSubscriptionRef} WHERE {order} = ?order ORDER BY {platform} ASC");
		query.addQueryParameter("order", order);
		return flexibleSearchService.<BillingSubscriptionRefModel> search(query).getResult();
	}

	public void setSubscriptionBillingService(final SubscriptionBillingService subscriptionBillingService)
	{
		this.subscriptionBillingService = subscriptionBillingService;
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}

	public void setSessionService(final SessionService sessionService)
	{
		this.sessionService = sessionService;
	}

	public void setBaseSiteService(final BaseSiteService baseSiteService)
	{
		this.baseSiteService = baseSiteService;
	}
}
