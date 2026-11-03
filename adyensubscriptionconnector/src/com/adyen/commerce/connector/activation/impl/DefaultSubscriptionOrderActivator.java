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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adyen.commerce.connector.activation.BillingActivationAttemptService;
import com.adyen.commerce.connector.activation.SubscriptionOrderActivator;
import com.adyen.commerce.connector.context.SubscriptionStoreContext;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.BillingException;
import com.adyen.commerce.connector.exception.PreconditionFailedException;
import com.adyen.commerce.connector.exception.SubscriptionProductUndecidableException;
import com.adyen.commerce.connector.log.ConnectorLogContext;
import com.adyen.commerce.connector.log.ConnectorLogEvent;
import com.adyen.commerce.connector.model.BillingActivationAttemptModel;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;
import com.adyen.commerce.connector.product.SubscriptionProductRule;
import com.adyen.commerce.connector.registry.SubscriptionBillingConnectorRegistry;
import com.adyen.commerce.connector.service.SubscriptionBillingService;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;

import de.hybris.platform.commerceservices.enums.CustomerType;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.servicelayer.session.SessionExecutionBody;
import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.store.BaseStoreModel;

/**
 * Turns a paid order into a subscription on the store's active billing platform.
 *
 * <p>Classification is {@link SubscriptionProductRule}'s, shared with the payment decorator. An
 * undecidable product is journalled and retried rather than read as "no"; a later "no" closes the journal row
 * as {@code NOT_APPLICABLE}. Activation is idempotent per order and platform, since a partial payment sends
 * one notification per leg.</p>
 *
 * <p>Connector calls run in a local session view with the order's store in context, because the callers are
 * notification and cron threads without one. Nothing escapes this class: the money has already moved, so every
 * failure is journalled for retry or the dead letter instead.</p>
 */
public class DefaultSubscriptionOrderActivator implements SubscriptionOrderActivator
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultSubscriptionOrderActivator.class);

	/** One event name for success, skip and failure, so they can be counted against each other. */
	private static final String EVENT_ACTIVATION = "subscription_activation";
	private static final String ORDER_CODE = "order_code";

	private SubscriptionBillingService subscriptionBillingService;
	private SubscriptionBillingConnectorRegistry connectorRegistry;
	private SubscriptionProductRule subscriptionProductRule;
	private BillingActivationAttemptService attemptService;
	private SessionService sessionService;
	private SubscriptionStoreContext storeContext;

	@Override
	public void activateFor(final OrderModel order)
	{
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
		// Most stores sell no subscriptions; they must cost nothing and log nothing here.
		if (store == null || store.getActiveBillingPlatform() == null)
		{
			return;
		}

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
		final long startedAt = System.nanoTime();
		try (ConnectorLogContext ignored = ConnectorLogContext.correlate(order.getCode()))
		{
			try
			{
				storeContext.establish(order, store);

				final SubscriptionBillingConnector connector = connectorRegistry.getActiveConnector(store);
				final ProductModel product = chooseSubscriptionProduct(order, connector);
				if (product == null)
				{
					// Closes a row left by an earlier undecidable attempt, which would otherwise dead-letter.
					attemptService.notApplicable(order, platform,
							"The subscription product rule answered for every entry on a later attempt and none of them "
									+ "is a subscription product");
					ConnectorLogEvent.of(EVENT_ACTIVATION)
							.platform(platform)
							.field(ORDER_CODE, order.getCode())
							.outcome(ConnectorLogEvent.OUTCOME_IGNORED)
							.reason("no subscription product on the order")
							.durationSince(startedAt)
							.debug(LOG);
					return;
				}

				attempt = attemptService.begin(order, platform, product.getCode(),
						subscriptionBillingService.idempotencyKeyFor(order));

				// After the journal is open, so the refusal is recorded against the product.
				requireShopperWhoCanManageIt(order);

				final BillingSubscriptionRefModel ref = subscriptionBillingService.activateSubscription(order, product);
				attemptService.succeeded(attempt, ref);

				ConnectorLogEvent.of(EVENT_ACTIVATION)
						.platform(platform)
						.field(ORDER_CODE, order.getCode())
						.field("product_code", product.getCode())
						.field("subscription_id", ref == null ? null : ref.getExternalSubscriptionId())
						.success(startedAt)
						.info(LOG);
			}
			catch (final BillingException | RuntimeException e)
			{
				final BillingException billingFailure = e instanceof BillingException billing ? billing : null;
				ConnectorLogEvent.of(EVENT_ACTIVATION)
						.platform(platform)
						.field(ORDER_CODE, order.getCode())
						.field("exception_class", e.getClass().getName())
						.failure(startedAt, billingFailure)
						.warn(LOG);
				recordFailure(order, platform, attempt, e);
			}
		}
	}

	/**
	 * The one subscription product to activate, or {@code null} if the order carries none.
	 *
	 * @throws SubscriptionProductUndecidableException if an entry could not be classified
	 * @throws PreconditionFailedException if the order carries more than one subscription unit (several products,
	 *         or a quantity above one); refused as a whole so no paid unit is silently dropped
	 */
	protected ProductModel chooseSubscriptionProduct(final OrderModel order, final SubscriptionBillingConnector connector)
			throws BillingException
	{
		final Map<String, SubscriptionUnits> products = subscriptionProducts(order, connector);
		if (products.isEmpty())
		{
			return null;
		}
		if (products.size() > 1)
		{
			throw new PreconditionFailedException("Order '" + order.getCode() + "' carries " + products.size()
					+ " subscription products " + products.keySet() + " but one order can hold one subscription; "
					+ "refusing to activate any of them. This order needs to be set up by hand");
		}
		final SubscriptionUnits only = products.values().iterator().next();
		if (only.quantity() > 1)
		{
			throw new PreconditionFailedException("Order '" + order.getCode() + "' carries subscription product '"
					+ only.product().getCode() + "' with quantity " + only.quantity() + " but a subscription is "
					+ "activated with quantity one; refusing to activate. This order needs to be set up by hand");
		}
		return only.product();
	}

	/**
	 * Refuses a guest: My Account is the only place to see or cancel a subscription, and a guest who registers
	 * becomes a different customer.
	 *
	 * @throws PreconditionFailedException for a guest
	 */
	protected void requireShopperWhoCanManageIt(final OrderModel order) throws PreconditionFailedException
	{
		if (order.getUser() instanceof CustomerModel customer && CustomerType.GUEST.equals(customer.getType()))
		{
			throw new PreconditionFailedException("Order '" + order.getCode() + "' was placed by a guest, who has no "
					+ "account through which a subscription could ever be seen or cancelled, and who would be given a "
					+ "different customer record on registering; refusing to start a recurring charge nobody can stop");
		}
	}

	/**
	 * Journals the failure for retry. A failure before the product was known opens a row with a {@code null}
	 * product code.
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
			LOG.error("Could not activate a {} subscription for order '{}', and could not record the attempt "
					+ "either. The order stands and the shopper was charged; this will not be retried.", platform,
					order.getCode(), failure);
			LOG.error("Recording the failed activation attempt for order '{}' failed with:", order.getCode(), e);
		}
	}

	/**
	 * Subscription products on the order with their total quantity, in entry order. The rule is asked once per
	 * product code, and an undecidable entry stops the scan so the order is retried rather than activated from an
	 * incomplete list.
	 */
	protected Map<String, SubscriptionUnits> subscriptionProducts(final OrderModel order,
			final SubscriptionBillingConnector connector) throws SubscriptionProductUndecidableException
	{
		final Map<String, SubscriptionUnits> products = new LinkedHashMap<>();
		final Map<String, Boolean> verdicts = new HashMap<>();
		for (final AbstractOrderEntryModel entry : order.getEntries())
		{
			final ProductModel product = entry == null ? null : entry.getProduct();
			if (product == null || StringUtils.isBlank(product.getCode()))
			{
				continue;
			}
			Boolean verdict = verdicts.get(product.getCode());
			if (verdict == null)
			{
				verdict = subscriptionProductRule.isSubscriptionProduct(connector, order.getStore(), product);
				verdicts.put(product.getCode(), verdict);
			}
			if (verdict)
			{
				products.merge(product.getCode(), new SubscriptionUnits(product, unitsOf(entry)),
						(known, added) -> new SubscriptionUnits(known.product(), known.quantity() + added.quantity()));
			}
		}
		return products;
	}

	/** An entry counts as at least one unit, so a missing quantity cannot hide a subscription. */
	protected long unitsOf(final AbstractOrderEntryModel entry)
	{
		final Long quantity = entry.getQuantity();
		return quantity == null ? 1L : Math.max(1L, quantity);
	}

	/** A subscription product and how many units of it the order carries. */
	protected record SubscriptionUnits(ProductModel product, long quantity)
	{
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

	public void setStoreContext(final SubscriptionStoreContext storeContext)
	{
		this.storeContext = storeContext;
	}
}
