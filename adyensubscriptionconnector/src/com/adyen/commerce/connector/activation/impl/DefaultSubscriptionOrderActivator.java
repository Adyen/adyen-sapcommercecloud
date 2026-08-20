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
import com.adyen.commerce.connector.dto.PlanResolutionRequest;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.BillingException;
import com.adyen.commerce.connector.exception.PlanNotMappedException;
import com.adyen.commerce.connector.exception.PreconditionFailedException;
import com.adyen.commerce.connector.model.BillingActivationAttemptModel;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;
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
 * <p>A product is one exactly when the active connector can resolve a plan for it. The rule lives in
 * {@link #isSubscriptionProduct} and nowhere else, so it can be replaced by overriding that one method.
 * Probing is safe and cheap: both shipped resolvers answer from a FlexibleSearch over their own mapping
 * table, with no remote call and no side effect. It does mean an unmapped product is indistinguishable
 * from a non-subscription one — the failure mode is "nothing happens", which is visible and harmless,
 * unlike the alternative below.</p>
 *
 * <p>The tempting shortcut — call {@code activateSubscription} for every entry and treat
 * {@link PlanNotMappedException} as "not a subscription" — is wrong. Inside the service, plan resolution
 * runs <em>after</em> {@code ensureCustomer} and {@code importAdyenToken}, so every ordinary line item in
 * the cart would leave a real customer and an imported payment token behind on the billing platform
 * before being rejected.</p>
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
				// Not a subscription order. Deliberately journalled as nothing at all: this is the ordinary
				// case for most orders in a store that happens to sell subscriptions too.
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
	 */
	protected ProductModel chooseSubscriptionProduct(final OrderModel order, final SubscriptionBillingConnector connector)
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
	 * it here. That does mean an ordinary, non-subscription order in a subscription-selling store can
	 * acquire a row when the store's own configuration is broken — which is the right noise to make: the
	 * same breakage is stopping every genuine subscription in that store too.</p>
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
	 */
	protected Map<String, ProductModel> subscriptionProducts(final OrderModel order,
			final SubscriptionBillingConnector connector)
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
			if (isSubscriptionProduct(connector, product))
			{
				products.put(product.getCode(), product);
			}
		}
		return products;
	}

	protected boolean isSubscriptionProduct(final SubscriptionBillingConnector connector, final ProductModel product)
	{
		try
		{
			connector.resolvePlan(new PlanResolutionRequest(product.getCode(), Map.of()));
			return true;
		}
		catch (final PlanNotMappedException e)
		{
			return false;
		}
		catch (final BillingException | RuntimeException e)
		{
			// Resolution is a local lookup, so this is a broken resolver rather than an ordinary product.
			// Skip it, but say so: silently treating it as "not a subscription" would hide the breakage.
			// RuntimeException is caught here rather than left to the outer guard on purpose — FlexibleSearch
			// throws unchecked, and letting it out would abandon every remaining entry instead of this one.
			LOG.warn("Could not decide whether product '{}' is a {} subscription product; skipping it.",
					product.getCode(), connector.platform(), e);
			return false;
		}
	}

	public void setSubscriptionBillingService(final SubscriptionBillingService subscriptionBillingService)
	{
		this.subscriptionBillingService = subscriptionBillingService;
	}

	public void setConnectorRegistry(final SubscriptionBillingConnectorRegistry connectorRegistry)
	{
		this.connectorRegistry = connectorRegistry;
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
