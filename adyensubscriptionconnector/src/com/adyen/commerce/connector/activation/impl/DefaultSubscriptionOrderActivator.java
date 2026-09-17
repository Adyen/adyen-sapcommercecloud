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
import com.adyen.commerce.connector.log.ConnectorLogContext;
import com.adyen.commerce.connector.log.ConnectorLogEvent;
import com.adyen.commerce.connector.model.BillingActivationAttemptModel;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;
import com.adyen.commerce.connector.product.SubscriptionProductRule;
import com.adyen.commerce.connector.registry.SubscriptionBillingConnectorRegistry;
import com.adyen.commerce.connector.service.SubscriptionBillingService;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;

import de.hybris.platform.basecommerce.model.site.BaseSiteModel;
import de.hybris.platform.commerceservices.enums.CustomerType;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.servicelayer.session.SessionExecutionBody;
import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.site.BaseSiteService;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;

/**
 * Turns a paid order into a subscription on the store's active billing platform.
 *
 * <p>What counts as a subscription product is {@link SubscriptionProductRule}'s decision, shared with
 * {@code SubscriptionPaymentRequestDecorator} so that the token forced at payment time and the activation
 * attempted afterwards always concern the same products. A
 * {@link SubscriptionProductUndecidableException} means the rule failed rather than answered "no", so it is
 * journalled and retried rather than downgraded to "ordinary order"; a later attempt that does answer "no"
 * closes the row as {@code NOT_APPLICABLE}, because the retry job turns a stale {@code FAILED} into a dead
 * letter claiming the shopper was charged for a subscription. A {@code null} product code on a journal row
 * does not by itself mean undecidable — an unconfigured connector and a precondition failure reach the
 * journal the same way, and only {@code lastError} tells them apart.</p>
 *
 * <p>Activation is idempotent on {@code (order, platform)} and keyed remotely on the order code, so calling
 * this twice for the same order returns the first reference; a partial payment produces one Adyen
 * notification per leg, so that happens routinely.</p>
 *
 * <p>Everything that can reach a connector runs in a session-local view with the order's base site
 * activated, because connector configuration is read from {@code baseStoreService.getCurrentBaseStore()}
 * and the callers arrive on bare notification and cron threads with no such context. The resolved store is
 * compared with the order's and activation refused when they differ: a base site listing several stores
 * resolves to its first one, so a mismatch means billing a different merchant account than the shopper was
 * quoted.</p>
 *
 * <p>Nothing escapes this class. Every caller is past the point where money has moved, so a billing
 * platform being down must not fail a checkout or reject a webhook; each attempt is journalled as a
 * {@code BillingActivationAttempt}, transient failures get a due date for the retry job, and terminal or
 * exhausted ones land in the dead letter.</p>
 */
public class DefaultSubscriptionOrderActivator implements SubscriptionOrderActivator
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultSubscriptionOrderActivator.class);

	/** One event name for all three outcomes, so success, skip and failure can be counted against each other. */
	private static final String EVENT_ACTIVATION = "subscription_activation";

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
		final long startedAt = System.nanoTime();
		// Ties every connector line logged underneath to the order that caused it, so a transport failure
		// three layers down is not an anonymous HTTP error.
		try (ConnectorLogContext correlation = ConnectorLogContext.correlate(order.getCode()))
		{
			try
			{
				establishStoreContext(order, store);

				final SubscriptionBillingConnector connector = connectorRegistry.getActiveConnector(store);
				final ProductModel product = chooseSubscriptionProduct(order, connector);
				if (product == null)
				{
					// null means the rule answered "no" for every entry; failing to answer arrives at the catch
					// below instead. Closes any row left over from an earlier attempt that could not answer, which
					// the retry job would otherwise abandon into a dead letter claiming the shopper was charged for
					// a subscription this order never contained.
					attemptService.notApplicable(order, platform,
							"The subscription product rule answered for every entry on a later attempt and none of them "
									+ "is a subscription product");
					// DEBUG because this is the ordinary answer for most orders in a subscription-selling store and
					// would drown the lines that mean something; logged at all because its absence is otherwise
					// indistinguishable from a trigger that never fired.
					ConnectorLogEvent.of(EVENT_ACTIVATION)
							.platform(platform)
							.field("order_code", order.getCode())
							.outcome(ConnectorLogEvent.OUTCOME_IGNORED)
							.reason("no subscription product on the order")
							.durationSince(startedAt)
							.debug(LOG);
					return;
				}

				attempt = attemptService.begin(order, platform, product.getCode(),
						subscriptionBillingService.idempotencyKeyFor(order));

				// After the journal is open, so the refusal is recorded against the product it concerns rather
				// than arriving in the dead letter with nothing to say about what was sold.
				requireShopperWhoCanManageIt(order);

				final BillingSubscriptionRefModel ref = subscriptionBillingService.activateSubscription(order, product);
				attemptService.succeeded(attempt, ref);

				ConnectorLogEvent.of(EVENT_ACTIVATION)
						.platform(platform)
						.field("order_code", order.getCode())
						.field("product_code", product.getCode())
						.field("subscription_id", ref == null ? null : ref.getExternalSubscriptionId())
						.success(startedAt)
						.info(LOG);
			}
			catch (final BillingException | RuntimeException e)
			{
				// One line per activation regardless of how it ended, so the three outcomes can be counted against
				// each other. recordFailure below writes the journal and only speaks up when it cannot.
				final BillingException billingFailure = e instanceof BillingException billing ? billing : null;
				ConnectorLogEvent.of(EVENT_ACTIVATION)
						.platform(platform)
						.field("order_code", order.getCode())
						.field("exception_class", e.getClass().getName())
						.failure(startedAt, billingFailure)
						.warn(LOG);
				recordFailure(order, platform, attempt, e);
			}
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
	 * <p>An order carrying more than one is refused rather than served in part: the reference type and the
	 * journal each hold one row per order and platform, so activating the first would leave the second
	 * product with no trace an operator could find. A dead letter naming both can be put right by hand.</p>
	 *
	 * @throws SubscriptionProductUndecidableException if any entry could not be classified, which is not the
	 *         same as {@code null} and must not become it — see the class javadoc
	 * @throws PreconditionFailedException if the order carries more than one subscription product; terminal,
	 *         because no amount of retrying will make the order carry fewer
	 */
	protected ProductModel chooseSubscriptionProduct(final OrderModel order, final SubscriptionBillingConnector connector)
			throws BillingException
	{
		final Map<String, ProductModel> products = subscriptionProducts(order, connector);
		if (products.isEmpty())
		{
			return null;
		}
		if (products.size() > 1)
		{
			throw new PreconditionFailedException("Order '" + order.getCode() + "' carries " + products.size()
					+ " subscription products " + products.keySet() + " but one order can hold one subscription; "
					+ "refusing to activate any of them rather than silently delivering one of the two the shopper "
					+ "paid for. This order needs to be set up by hand, and the cart rule that let it through needs "
					+ "fixing");
		}
		return products.values().iterator().next();
	}

	/**
	 * Refuses to activate a subscription for a shopper who would never be able to reach it.
	 *
	 * <p>The My Account panel is the only place a subscription can be seen or cancelled and sits behind
	 * {@code ROLE_CUSTOMERGROUP}, which a guest never has. A guest who later registers is given a new
	 * {@code Customer} while the reference stays on the old one, so the subscription is unreachable
	 * permanently rather than merely until they sign up.</p>
	 *
	 * @throws PreconditionFailedException for a guest; terminal, because retrying will not register them
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
	 * Writes the failure to the journal so it can be retried or found later.
	 *
	 * <p>A failure from before the product was known has no record open yet, so one is opened here with a
	 * {@code null} product code. An ordinary order in a subscription-selling store can therefore acquire a
	 * row when the store's configuration is broken, which is intended: the same breakage stops every genuine
	 * subscription in that store, and a
	 * {@link com.adyen.commerce.connector.exception.SubscriptionProductUndecidableException} means nobody can
	 * say whether this order was one of them.</p>
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
			// If even the journal cannot be written the failure would vanish, so both the original cause and
			// the reason it was not recorded go to the log at full volume.
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
	 * <p>An entry the rule cannot classify stops the scan rather than being skipped: skipping it would make an
	 * order whose only subscription entry is the unclassifiable one look like an ordinary order, which is
	 * journalled as nothing at all. A mixed order therefore defers to the retry instead of going ahead on the
	 * entry that did resolve — going ahead would pick from an incomplete list, and a subscription on the wrong
	 * plan is not revisited because the order already has one.</p>
	 *
	 * <p>{@code SubscriptionPaymentRequestDecorator} scans every entry too, so that the two agree on a mixed
	 * cart regardless of entry order.</p>
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
