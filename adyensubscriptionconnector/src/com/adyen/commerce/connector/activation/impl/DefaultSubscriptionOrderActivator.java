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

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adyen.commerce.connector.activation.BillingActivationAttemptService;
import com.adyen.commerce.connector.activation.SubscriptionOrderActivator;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.BillingException;
import com.adyen.commerce.connector.exception.PreconditionFailedException;
import com.adyen.commerce.connector.exception.SubscriptionProductUndecidableException;
import com.adyen.commerce.connector.model.BillingActivationAttemptModel;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;
import com.adyen.commerce.connector.product.SubscriptionProductRule;
import com.adyen.commerce.connector.registry.SubscriptionBillingConnectorRegistry;
import com.adyen.commerce.connector.service.SubscriptionBillingService;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;

import de.hybris.platform.basecommerce.model.site.BaseSiteModel;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.servicelayer.session.SessionExecutionBody;
import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.site.BaseSiteService;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;

/**
 * <h3>What counts as a subscription product</h3>
 * <p>Not this class's decision. It lives in {@link SubscriptionProductRule} and nowhere else, so it can
 * be replaced by replacing that one bean — and, more to the point, so that this class and
 * {@code SubscriptionPaymentRequestDecorator} cannot drift apart. The decorator asks the same rule before
 * the shopper is charged, to decide whether the payment has to leave a reusable token behind; if the two
 * disagreed, an order would be tokenized with nothing to activate or activated with nothing to charge.</p>
 *
 * <p>What this class owns is the answer to a rule that <em>cannot</em> answer, and it is the opposite of
 * the decorator's. A {@link SubscriptionProductUndecidableException} means a resolver failed rather than
 * said no, so the order may well be a subscription order — and this runs after the money has moved, where
 * refusing to answer must not turn into a failed checkout. It is therefore journalled like any other
 * activation failure and left to the retry job, rather than downgraded to "ordinary order". Downgrading is
 * what used to happen, and it was the one path that produced a paid order with no journal row at all:
 * nothing was attempted, so nothing was recorded, so nothing was ever retried.</p>
 *
 * <p>Such a row is not left to rot. When a later attempt does get an answer and the answer is "no
 * subscription product here", the row is closed as {@code NOT_APPLICABLE} rather than left {@code FAILED},
 * because the retry job abandons a stale {@code FAILED} into a dead letter announcing that a shopper was
 * charged for a subscription — which for an ordinary order that met one resolver blip would be false. Note
 * that {@code productCode == null} alone does <em>not</em> identify the undecidable case: a connector that
 * is not configured, or a precondition failure, reaches the journal the same way. The {@code lastError} is
 * what tells them apart, and only until a terminal outcome rewrites it.</p>
 *
 * <h3>One subscription per order</h3>
 * <p>The service is idempotent on {@code (order, platform)} and keys the remote call on the order code,
 * so a second activation for the same order returns the first reference rather than creating anything.
 * That also makes this safe to call more than once for the same order, which matters because a partial
 * payment produces one Adyen notification per leg. It does mean an order carrying several subscription
 * products activates only the first; that is logged rather than hidden.</p>
 *
 * <h3>Which store's credentials get used</h3>
 * <p>Everything that can reach a connector runs inside a session-local view with the order's own base
 * site activated, because the connectors' configuration services read their credentials from
 * {@code baseStoreService.getCurrentBaseStore()}. The callers of this class have no such context to
 * offer: an Adyen notification arrives on a bare worker thread and the retry job on a cron thread, and
 * without this both would read no configuration at all.</p>
 *
 * <p>The resolved store is then checked against the order's, and activation is refused outright when
 * they differ. That check is not defensive padding — a base site listing several stores resolves to its
 * first one, so a mismatch means the connector would be about to charge a different merchant account
 * than the one the shopper was quoted.</p>
 *
 * <h3>Failure handling</h3>
 * <p>Nothing escapes. Every caller is on a payment or checkout path where the money has already moved, so
 * a billing platform being down must not turn into a failed checkout or a rejected webhook. What has
 * changed is that a swallowed failure is no longer a lost one: every attempt is journalled as a
 * {@code BillingActivationAttempt}, transient failures are given a due date for the retry job, and
 * anything terminal or out of retries lands in the dead letter where an operator can find it.</p>
 */
public class DefaultSubscriptionOrderActivator implements SubscriptionOrderActivator
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultSubscriptionOrderActivator.class);

	private SubscriptionBillingService subscriptionBillingService;
	private SubscriptionBillingConnectorRegistry connectorRegistry;
	private SubscriptionProductRule subscriptionProductRule;
	private BillingActivationAttemptService attemptService;
	private SessionService sessionService;
	private BaseSiteService baseSiteService;
	private BaseStoreService baseStoreService;

	@Override
	public void activateFor(final OrderModel order)
	{
		// The whole body is inside the guard, not just the activation call: reading the order or its entries
		// can fail too, and this method must not be able to throw at all.
		try
		{
			doActivateFor(order);
		}
		catch (final RuntimeException e)
		{
			LOG.error("Unexpected failure while activating a subscription. The order stands.", e);
		}
	}

	protected void doActivateFor(final OrderModel order)
	{
		if (order == null || CollectionUtils.isEmpty(order.getEntries()))
		{
			return;
		}

		final BaseStoreModel store = order.getStore();
		// Read the platform directly rather than asking the registry: a store that simply does not sell
		// subscriptions is the common case, and it must cost nothing and log nothing on every order.
		if (store == null || store.getActiveBillingPlatform() == null)
		{
			return;
		}

		// Local view rather than the caller's session: the base site set here must not leak back out into
		// whatever thread this is running on.
		sessionService.executeInLocalView(new SessionExecutionBody()
		{
			@Override
			public void executeWithoutResult()
			{
				activateInStoreContext(order, store);
			}
		});
	}

	protected void activateInStoreContext(final OrderModel order, final BaseStoreModel store)
	{
		final BillingPlatform platform = store.getActiveBillingPlatform();
		BillingActivationAttemptModel attempt = null;
		try
		{
			establishStoreContext(order, store);

			final SubscriptionBillingConnector connector = connectorRegistry.getActiveConnector(store);
			final ProductModel product = chooseSubscriptionProduct(order, connector);
			if (product == null)
			{
				// Not a subscription order, and now that really is what null means: the rule answered "no" for
				// every entry rather than failing to answer for one of them, which arrives at the catch below
				// instead. Nothing is journalled for the ordinary case — most orders in a store that happens to
				// sell subscriptions come through here. But a row may already exist from an earlier run where the
				// rule could not answer, and leaving it FAILED would let the retry job abandon it into a dead
				// letter claiming a shopper was charged for a subscription this order never contained.
				attemptService.notApplicable(order, platform,
						"The subscription product rule answered for every entry on a later attempt and none of them "
								+ "is a subscription product");
				return;
			}

			attempt = attemptService.begin(order, platform, product.getCode(),
					subscriptionBillingService.idempotencyKeyFor(order));

			final BillingSubscriptionRefModel ref = subscriptionBillingService.activateSubscription(order, product);
			attemptService.succeeded(attempt, ref);
		}
		catch (final BillingException | RuntimeException e)
		{
			recordFailure(order, platform, attempt, e);
		}
	}

	/**
	 * Activates the order's base site in the local view and confirms it resolves to the order's own store.
	 *
	 * @throws PreconditionFailedException when it does not, which is terminal by nature: the mapping from
	 *         site to store is configuration, and no amount of retrying will change it
	 */
	protected void establishStoreContext(final OrderModel order, final BaseStoreModel store)
			throws PreconditionFailedException
	{
		final BaseSiteModel site = order.getSite();
		if (site != null)
		{
			// false: catalog versions are not needed to read a store's connector credentials, and activating
			// them is the expensive half of this call.
			baseSiteService.setCurrentBaseSite(site, false);
		}

		final BaseStoreModel resolved = baseStoreService.getCurrentBaseStore();
		if (resolved == null)
		{
			throw new PreconditionFailedException("Order '" + order.getCode() + "' resolves to no current base store"
					+ (site == null ? " because it has no base site" : " via base site '" + site.getUid()
							+ "', which lists no stores")
					+ "; refusing to activate a subscription without knowing whose credentials to use");
		}
		// By PK rather than by model identity: the two can be different instances of the same store.
		if (!Objects.equals(store.getPk(), resolved.getPk()))
		{
			throw new PreconditionFailedException("Order '" + order.getCode() + "' belongs to base store '"
					+ store.getUid() + "' but its base site resolves to '" + resolved.getUid()
					+ "'; refusing to activate, because the connector would read the wrong store's credentials "
					+ "and bill against the wrong merchant account");
		}
	}

	/**
	 * The one subscription product to activate, or {@code null} if the order carries none.
	 *
	 * @throws SubscriptionProductUndecidableException if any entry could not be classified, which is not the
	 *         same as {@code null} and must not become it — see the class javadoc
	 */
	protected ProductModel chooseSubscriptionProduct(final OrderModel order, final SubscriptionBillingConnector connector)
			throws SubscriptionProductUndecidableException
	{
		final Map<String, ProductModel> products = subscriptionProducts(order, connector);
		if (products.isEmpty())
		{
			return null;
		}

		final ProductModel product = products.values().iterator().next();
		if (products.size() > 1)
		{
			LOG.warn("Order '{}' carries {} subscription products {} but one order can hold one subscription; "
					+ "activating '{}' and leaving the rest inactive.", order.getCode(), products.size(),
					products.keySet(), product.getCode());
		}
		return product;
	}

	/**
	 * Writes the failure to the journal so it can be retried or found later.
	 *
	 * <p>A failure that happened before the product was known has no record open yet, and one is opened for
	 * it here, with a {@code null} product code because there genuinely is not one. That does mean an
	 * ordinary, non-subscription order in a subscription-selling store can acquire a row when the store's own
	 * configuration is broken — which is the right noise to make: the same breakage is stopping every genuine
	 * subscription in that store too, and a
	 * {@link com.adyen.commerce.connector.exception.SubscriptionProductUndecidableException} in particular
	 * means nobody can say whether this order was one of them.</p>
	 */
	protected void recordFailure(final OrderModel order, final BillingPlatform platform,
			final BillingActivationAttemptModel openAttempt, final Exception failure)
	{
		BillingActivationAttemptModel attempt = openAttempt;
		try
		{
			if (attempt == null)
			{
				attempt = attemptService.begin(order, platform, null, subscriptionBillingService.idempotencyKeyFor(order));
			}
			attemptService.failed(attempt, failure);
		}
		catch (final RuntimeException e)
		{
			// Last line of defence. If even the journal cannot be written the failure would otherwise vanish,
			// so it goes to the log at full volume, with both the original cause and the reason it was not
			// recorded.
			LOG.error("Could not activate a {} subscription for order '{}', and could not record the attempt "
					+ "either. The order stands and the shopper was charged; this will not be retried.", platform,
					order.getCode(), failure);
			LOG.error("Recording the failed activation attempt for order '{}' failed with:", order.getCode(), e);
		}
	}

	/**
	 * Keyed by product code so the same product ordered on several entries counts once, and ordered so the
	 * chosen one does not depend on map iteration order.
	 *
	 * <p>An entry the rule cannot classify stops the scan instead of being skipped. Skipping it was the old
	 * behaviour and it is unsound: an order whose only subscription entry is the unclassifiable one then looks
	 * like an ordinary order, and an ordinary order is journalled as nothing at all.</p>
	 *
	 * <p>The price is paid by the mixed order — one entry the rule cannot classify, another it can — whose
	 * activation is now deferred to the retry instead of going ahead on the entry that did resolve. That is
	 * the intended trade. Only one subscription per order is activated and the choice is positional — the
	 * first match in entry order, see {@code chooseSubscriptionProduct} — so going ahead while one candidate
	 * is invisible means the choice was made from an incomplete list, silently and unrepeatably. Deferring
	 * costs a retry; choosing wrongly costs a subscription on the wrong plan, which a retry will not revisit
	 * because the order already has one. In practice the resolvers are backed by one FlexibleSearch each, so a
	 * failure that hits one entry has usually hit all of them anyway.</p>
	 *
	 * <p>{@code SubscriptionPaymentRequestDecorator} scans every entry for the same reason, so that the two
	 * agree on a mixed cart regardless of the order its entries happen to be in.</p>
	 */
	protected Map<String, ProductModel> subscriptionProducts(final OrderModel order,
			final SubscriptionBillingConnector connector) throws SubscriptionProductUndecidableException
	{
		final Map<String, ProductModel> products = new LinkedHashMap<>();
		// Tracked separately from the result: keying only on matches would re-query an ordinary product
		// once per entry it appears on.
		final Set<String> seen = new HashSet<>();
		for (final AbstractOrderEntryModel entry : order.getEntries())
		{
			final ProductModel product = entry == null ? null : entry.getProduct();
			if (product == null || StringUtils.isBlank(product.getCode()) || !seen.add(product.getCode()))
			{
				continue;
			}
			if (subscriptionProductRule.isSubscriptionProduct(connector, product))
			{
				products.put(product.getCode(), product);
			}
		}
		return products;
	}

	public void setSubscriptionBillingService(final SubscriptionBillingService subscriptionBillingService)
	{
		this.subscriptionBillingService = subscriptionBillingService;
	}

	public void setConnectorRegistry(final SubscriptionBillingConnectorRegistry connectorRegistry)
	{
		this.connectorRegistry = connectorRegistry;
	}

	public void setSubscriptionProductRule(final SubscriptionProductRule subscriptionProductRule)
	{
		this.subscriptionProductRule = subscriptionProductRule;
	}

	public void setAttemptService(final BillingActivationAttemptService attemptService)
	{
		this.attemptService = attemptService;
	}

	public void setSessionService(final SessionService sessionService)
	{
		this.sessionService = sessionService;
	}

	public void setBaseSiteService(final BaseSiteService baseSiteService)
	{
		this.baseSiteService = baseSiteService;
	}

	public void setBaseStoreService(final BaseStoreService baseStoreService)
	{
		this.baseStoreService = baseStoreService;
	}
}
