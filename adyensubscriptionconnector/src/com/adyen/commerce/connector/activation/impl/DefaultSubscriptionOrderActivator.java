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
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adyen.commerce.connector.activation.SubscriptionOrderActivator;
import com.adyen.commerce.connector.dto.PlanResolutionRequest;
import com.adyen.commerce.connector.exception.BillingException;
import com.adyen.commerce.connector.exception.PlanNotMappedException;
import com.adyen.commerce.connector.registry.SubscriptionBillingConnectorRegistry;
import com.adyen.commerce.connector.service.SubscriptionBillingService;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;

import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.servicelayer.exceptions.ModelSavingException;
import de.hybris.platform.store.BaseStoreModel;

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
 * <h3>Failure handling</h3>
 * <p>Nothing escapes. Every caller is on a payment or checkout path where the money has already moved, so
 * a billing platform being down must not turn into a failed checkout or a rejected webhook. Failures are
 * logged and swallowed; there is no retry yet, so a logged failure needs manual follow-up.</p>
 */
public class DefaultSubscriptionOrderActivator implements SubscriptionOrderActivator
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultSubscriptionOrderActivator.class);

	private SubscriptionBillingService subscriptionBillingService;
	private SubscriptionBillingConnectorRegistry connectorRegistry;

	@Override
	public void activateFor(final OrderModel order)
	{
		activateOrder(result == null ? null : result.getOrder());
	}

	/**
	 * Shared entry point for the synchronous place-order hook and the post-3DS payment event.
	 * Activation is idempotent in {@link SubscriptionBillingService}, and no failure may escape into
	 * either checkout completion path after the shopper has already been charged.
	 */
	public void activateOrder(final OrderModel order)
	{
		// The whole body is inside the guard, not just the activation call: reading the order or its entries
		// can fail too, and this method must not be able to throw at all.
		try
		{
			activateIfSubscriptionOrder(order);
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

		activateSubscription(order, store);
	}

	protected void activateSubscription(final OrderModel order, final BaseStoreModel store)
	{
		final SubscriptionBillingConnector connector;
		try
		{
			connector = connectorRegistry.getActiveConnector(store);
		}
		catch (final BillingException e)
		{
			LOG.error("Base store '{}' selects billing platform {} but no connector answers for it; "
					+ "order '{}' placed without a subscription.", store.getUid(), store.getActiveBillingPlatform(),
					order.getCode(), e);
			return;
		}

		final Map<String, ProductModel> products = subscriptionProducts(order, connector);
		if (products.isEmpty())
		{
			return;
		}

		final ProductModel product = products.values().iterator().next();
		if (products.size() > 1)
		{
			LOG.warn("Order '{}' carries {} subscription products {} but one order can hold one subscription; "
					+ "activating '{}' and leaving the rest inactive.", order.getCode(), products.size(),
					products.keySet(), product.getCode());
		}

		try
		{
			subscriptionBillingService.activateSubscription(order, product);
		}
		catch (final ModelSavingException e)
		{
			// Expected under concurrency rather than broken: the unique index on (order, platform) is what
			// stops two notifications for the same order from both getting past the service's read-then-write
			// idempotency check. Whoever lost the race has nothing to do — the subscription exists.
			LOG.info("A {} subscription for order '{}' was persisted by another thread; nothing to do.",
					connector.platform(), order.getCode());
		}
		catch (final BillingException e)
		{
			LOG.error("Could not activate a {} subscription for order '{}' / product '{}'. The order stands and the "
					+ "shopper was charged; there is no automatic retry, so this needs manual follow-up.",
					connector.platform(), order.getCode(), product.getCode(), e);
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
}
