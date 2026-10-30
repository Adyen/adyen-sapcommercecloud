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
package com.adyen.commerce.connector.cart.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adyen.commerce.connector.cart.CartAdjustment;
import com.adyen.commerce.connector.cart.CartAssessment;
import com.adyen.commerce.connector.cart.SubscriptionCartGuard;
import com.adyen.commerce.connector.cart.SubscriptionCartStatus;
import com.adyen.commerce.connector.duplicate.DuplicateCheckRequest;
import com.adyen.commerce.connector.duplicate.DuplicateCheckStage;
import com.adyen.commerce.connector.duplicate.Freshness;
import com.adyen.commerce.connector.duplicate.SubscriptionOwnershipService;
import com.adyen.commerce.connector.duplicate.impl.DefaultSubscriptionOwnershipService;
import com.adyen.commerce.connector.exception.ConnectorNotConfiguredException;
import com.adyen.commerce.connector.exception.SubscriptionProductUndecidableException;
import com.adyen.commerce.connector.log.ConnectorLogEvent;
import com.adyen.commerce.connector.product.SubscriptionProductRule;
import com.adyen.commerce.connector.registry.SubscriptionBillingConnectorRegistry;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;

import de.hybris.platform.commerceservices.enums.CustomerType;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.CartEntryModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.user.UserService;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;

/**
 * Default cart rules for subscription products: not one the customer already owns, one subscription product per
 * cart, quantity one.
 *
 * <p>Which products are subscriptions is decided by the {@link SubscriptionProductRule} against the cart's store,
 * the same rule that decides tokenization and activation. Ownership is asked of the
 * {@link SubscriptionOwnershipService} with {@link Freshness#LOCAL}: the cart never calls a billing platform.
 * Anonymous and guest carts get no ownership check (they own nothing, and matching a guest by e-mail would tell an
 * anonymous visitor whether that e-mail has a subscription) but keep the limits.</p>
 *
 * <p>The rules mirror what {@code DefaultSubscriptionOrderActivator} enforces after payment (one product,
 * quantity one), moved in front of it.</p>
 */
public class DefaultSubscriptionCartGuard implements SubscriptionCartGuard
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultSubscriptionCartGuard.class);

	static final String EVENT_ADJUSTED = "subscription_cart_adjusted";
	static final String REASON_PRODUCT_UNDECIDABLE = "product_undecidable";
	static final String REASON_LIMIT_PER_CART = "limit_per_cart";
	static final String REASON_QUANTITY_LIMIT = "quantity_limit";

	private SubscriptionBillingConnectorRegistry connectorRegistry;
	private SubscriptionProductRule subscriptionProductRule;
	private SubscriptionOwnershipService subscriptionOwnershipService;
	private BaseStoreService baseStoreService;
	private ModelService modelService;
	private UserService userService;

	@Override
	public Optional<CartAdjustment> checkAddedEntry(final CartModel cart, final AbstractOrderEntryModel entry)
	{
		final Evaluation evaluation = open(cart, DuplicateCheckStage.ADD_TO_CART);
		if (evaluation == null || entry == null)
		{
			return Optional.empty();
		}
		return evaluate(evaluation, entry, heldByOthers(evaluation, entry), true);
	}

	@Override
	public Optional<CartAdjustment> checkUpdatedEntry(final CartModel cart, final AbstractOrderEntryModel entry)
	{
		final Evaluation evaluation = open(cart, DuplicateCheckStage.UPDATE_QUANTITY);
		if (evaluation == null || entry == null)
		{
			return Optional.empty();
		}
		return evaluate(evaluation, entry, heldByOthers(evaluation, entry), false);
	}

	@Override
	public List<CartAdjustment> checkCart(final CartModel cart)
	{
		final Evaluation evaluation = open(cart, DuplicateCheckStage.CART_VALIDATION);
		if (evaluation == null || cart.getEntries() == null)
		{
			return List.of();
		}
		return evaluateEntries(evaluation, cart, new ArrayList<>());
	}

	@Override
	public CartAssessment assessForAuthorisation(final CartModel cart)
	{
		final Evaluation evaluation = open(cart, DuplicateCheckStage.PRE_AUTH);
		if (evaluation == null || cart.getEntries() == null)
		{
			return CartAssessment.none();
		}
		evaluation.skipOwnership();
		final List<AbstractOrderEntryModel> subscriptionEntries = new ArrayList<>();
		for (final AbstractOrderEntryModel entry : cart.getEntries())
		{
			if (entry != null && entry.getProduct() != null && StringUtils.isNotBlank(entry.getProduct().getCode())
					&& isSubscriptionProduct(evaluation, entry.getProduct()))
			{
				subscriptionEntries.add(entry);
			}
		}
		return new CartAssessment(evaluation.store(), subscriptionEntries, evaluateEntries(evaluation, cart, new ArrayList<>()));
	}

	/** The adjustments for the whole cart in entry order: the first subscription product wins, the rest go. */
	protected List<CartAdjustment> evaluateEntries(final Evaluation evaluation, final CartModel cart, final List<Held> held)
	{
		final List<CartAdjustment> adjustments = new ArrayList<>();
		// A copy: applying an adjustment changes the cart's own list.
		for (final AbstractOrderEntryModel entry : new ArrayList<>(cart.getEntries()))
		{
			final Optional<CartAdjustment> adjustment = evaluate(evaluation, entry, held, true);
			if (adjustment.isPresent())
			{
				adjustments.add(adjustment.get());
				if (!adjustment.get().removesEntry())
				{
					held.add(new Held(entry.getProduct().getCode(), adjustment.get().keepQuantity()));
				}
			}
			else if (entry != null && entry.getProduct() != null && isSubscriptionProduct(evaluation, entry.getProduct()))
			{
				held.add(new Held(entry.getProduct().getCode(), quantityOf(entry)));
			}
		}
		return adjustments;
	}

	@Override
	public void apply(final CartModel cart, final CartAdjustment adjustment)
	{
		final AbstractOrderEntryModel entry = adjustment.entry();
		if (adjustment.removesEntry())
		{
			modelService.remove(entry);
		}
		else
		{
			entry.setQuantity(Long.valueOf(adjustment.keepQuantity()));
			modelService.save(entry);
		}
		modelService.refresh(cart);
		logAdjusted(cart, adjustment);
	}

	@Override
	public CartEntryModel emptyEntryFor(final ProductModel product)
	{
		final CartEntryModel entry = new CartEntryModel()
		{
			@Override
			public Double getBasePrice()
			{
				return null;
			}

			@Override
			public Double getTotalPrice()
			{
				return null;
			}
		};
		entry.setProduct(product);
		return entry;
	}

	// --- the rules ---

	/**
	 * @param held      the subscription entries that stay, ahead of this one
	 * @param fullCheck whether ownership and the one-product limit apply, or only the quantity limit
	 */
	protected Optional<CartAdjustment> evaluate(final Evaluation evaluation, final AbstractOrderEntryModel entry,
			final List<Held> held, final boolean fullCheck)
	{
		final ProductModel product = entry == null ? null : entry.getProduct();
		if (product == null || StringUtils.isBlank(product.getCode()) || !isSubscriptionProduct(evaluation, product))
		{
			return Optional.empty();
		}
		if (fullCheck && isOwned(evaluation, product))
		{
			return adjustment(evaluation, entry, SubscriptionCartStatus.ALREADY_OWNED, 0);
		}
		long sameProduct = 0;
		for (final Held other : held)
		{
			if (!other.productCode().equals(product.getCode()))
			{
				if (fullCheck)
				{
					return adjustment(evaluation, entry, SubscriptionCartStatus.LIMIT_PER_CART, 0);
				}
				continue;
			}
			sameProduct += other.quantity();
		}
		final long quantity = quantityOf(entry);
		if (sameProduct + quantity > 1)
		{
			return adjustment(evaluation, entry, SubscriptionCartStatus.QUANTITY_LIMIT, Math.max(0, 1 - sameProduct));
		}
		return Optional.empty();
	}

	protected Optional<CartAdjustment> adjustment(final Evaluation evaluation, final AbstractOrderEntryModel entry,
			final String statusCode, final long keepQuantity)
	{
		return Optional.of(new CartAdjustment(entry, statusCode, keepQuantity, evaluation.stage()));
	}

	/** The subscription entries of the cart other than the one being checked, with the quantities they have. */
	protected List<Held> heldByOthers(final Evaluation evaluation, final AbstractOrderEntryModel focus)
	{
		final List<Held> held = new ArrayList<>();
		if (evaluation.cart().getEntries() == null)
		{
			return held;
		}
		for (final AbstractOrderEntryModel other : evaluation.cart().getEntries())
		{
			if (other == null || other == focus || other.equals(focus) || other.getProduct() == null
					|| StringUtils.isBlank(other.getProduct().getCode()))
			{
				continue;
			}
			if (isSubscriptionProduct(evaluation, other.getProduct()))
			{
				held.add(new Held(other.getProduct().getCode(), quantityOf(other)));
			}
		}
		return held;
	}

	/** An entry counts as at least one unit when its quantity is missing, so it cannot hide a subscription. */
	protected long quantityOf(final AbstractOrderEntryModel entry)
	{
		final Long quantity = entry.getQuantity();
		return quantity == null ? 1L : quantity;
	}

	/** The rule's verdict, asked once per product for one evaluation. */
	protected boolean isSubscriptionProduct(final Evaluation evaluation, final ProductModel product)
	{
		final Boolean known = evaluation.verdicts().get(product.getCode());
		if (known != null)
		{
			return known;
		}
		boolean verdict;
		try
		{
			verdict = subscriptionProductRule.isSubscriptionProduct(evaluation.connector(), evaluation.store(), product);
		}
		catch (final SubscriptionProductUndecidableException e)
		{
			// Not knowing is not grounds to refuse a purchase, at any stage: an undecidable product is treated as not a
			// subscription (fail open: a billing-platform outage must not block purchases), so the payment check passes
			// it too, and only the degraded log line records it.
			ConnectorLogEvent.of(DefaultSubscriptionOwnershipService.EVENT_DEGRADED)
					.platform(evaluation.connector().platform())
					.field("stage", evaluation.stage().logValue())
					.field("store", evaluation.store().getUid())
					.reason(REASON_PRODUCT_UNDECIDABLE)
					.field("exception_class", e.getClass().getName())
					.warn(LOG);
			verdict = false;
		}
		evaluation.verdicts().put(product.getCode(), verdict);
		return verdict;
	}

	/** Whether the cart's customer already owns this subscription. Never for an anonymous or guest cart. */
	protected boolean isOwned(final Evaluation evaluation, final ProductModel product)
	{
		if (!evaluation.checksOwnership()
				|| !(evaluation.cart().getUser() instanceof CustomerModel customer) || userService.isAnonymousUser(customer)
				|| CustomerType.GUEST.equals(customer.getType()))
		{
			return false;
		}
		return evaluation.owned().computeIfAbsent(product.getCode(),
				code -> subscriptionOwnershipService.findBlocking(new DuplicateCheckRequest(customer, product,
						evaluation.store(), Freshness.LOCAL, evaluation.cart(), evaluation.stage())).isPresent());
	}

	/**
	 * @return the evaluation for the cart, or {@code null} when nothing in it can be a subscription: no store to
	 *         sell in, or a store without a billing platform
	 */
	protected Evaluation open(final CartModel cart, final DuplicateCheckStage stage)
	{
		if (cart == null)
		{
			return null;
		}
		final BaseStoreModel store = cart.getStore() != null ? cart.getStore() : baseStoreService.getCurrentBaseStore();
		if (store == null)
		{
			return null;
		}
		try
		{
			return new Evaluation(cart, store, connectorRegistry.getActiveConnector(store), stage);
		}
		catch (final ConnectorNotConfiguredException e)
		{
			return null;
		}
	}

	protected void logAdjusted(final CartModel cart, final CartAdjustment adjustment)
	{
		// An owned subscription is already logged as subscription_duplicate_blocked, by the check that found it.
		final String reason = switch (adjustment.statusCode())
		{
			case SubscriptionCartStatus.LIMIT_PER_CART -> REASON_LIMIT_PER_CART;
			case SubscriptionCartStatus.QUANTITY_LIMIT -> REASON_QUANTITY_LIMIT;
			default -> null;
		};
		if (reason == null)
		{
			return;
		}
		ConnectorLogEvent.of(EVENT_ADJUSTED)
				.field("stage", adjustment.stage() == null ? "unknown" : adjustment.stage().logValue())
				.reason(reason)
				.field("store", cart.getStore() == null ? null : cart.getStore().getUid())
				.info(LOG);
	}

	/** One subscription entry that stays in the cart. */
	protected record Held(String productCode, long quantity)
	{
	}

	/** What one pass over a cart works with; the verdicts are remembered so each product is classified once. */
	protected final class Evaluation
	{
		private final CartModel cart;
		private final BaseStoreModel store;
		private final SubscriptionBillingConnector connector;
		private final DuplicateCheckStage stage;
		private final Map<String, Boolean> verdicts = new HashMap<>();
		private final Map<String, Boolean> owned = new HashMap<>();
		private boolean checksOwnership = true;

		/** Public on a protected class: a constructor that is merely protected could not be called by a subclass of the guard. */
		public Evaluation(final CartModel cart, final BaseStoreModel store, final SubscriptionBillingConnector connector,
				final DuplicateCheckStage stage)
		{
			this.cart = cart;
			this.store = store;
			this.connector = connector;
			this.stage = stage;
		}

		/** Leaves the ownership question out of this evaluation, for a caller that asks it another way. */
		protected void skipOwnership()
		{
			checksOwnership = false;
		}

		protected boolean checksOwnership()
		{
			return checksOwnership;
		}

		protected CartModel cart()
		{
			return cart;
		}

		protected BaseStoreModel store()
		{
			return store;
		}

		protected SubscriptionBillingConnector connector()
		{
			return connector;
		}

		protected DuplicateCheckStage stage()
		{
			return stage;
		}

		/** The verdict per product code, remembered for this evaluation. */
		protected Map<String, Boolean> verdicts()
		{
			return verdicts;
		}

		/** Whether the customer owns the product, per product code, remembered for this evaluation. */
		protected Map<String, Boolean> owned()
		{
			return owned;
		}
	}

	public void setConnectorRegistry(final SubscriptionBillingConnectorRegistry connectorRegistry)
	{
		this.connectorRegistry = connectorRegistry;
	}

	public void setSubscriptionProductRule(final SubscriptionProductRule subscriptionProductRule)
	{
		this.subscriptionProductRule = subscriptionProductRule;
	}

	public void setSubscriptionOwnershipService(final SubscriptionOwnershipService subscriptionOwnershipService)
	{
		this.subscriptionOwnershipService = subscriptionOwnershipService;
	}

	public void setBaseStoreService(final BaseStoreService baseStoreService)
	{
		this.baseStoreService = baseStoreService;
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}

	public void setUserService(final UserService userService)
	{
		this.userService = userService;
	}
}
