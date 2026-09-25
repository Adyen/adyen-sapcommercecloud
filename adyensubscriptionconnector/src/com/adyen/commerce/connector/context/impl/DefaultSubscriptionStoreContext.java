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
package com.adyen.commerce.connector.context.impl;

import java.util.Collections;
import java.util.Objects;

import com.adyen.commerce.connector.context.SubscriptionBaseStoreSelectorStrategy;
import com.adyen.commerce.connector.context.SubscriptionStoreContext;
import com.adyen.commerce.connector.exception.BillingException;
import com.adyen.commerce.connector.exception.PreconditionFailedException;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;

import de.hybris.platform.basecommerce.model.site.BaseSiteModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.servicelayer.session.SessionExecutionBody;
import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.site.BaseSiteService;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;


/**
 * Pins the subscription's store through {@link SubscriptionBaseStoreSelectorStrategy} and activates the
 * order's site, both inside a local session view so nothing leaks back to the caller.
 */
public class DefaultSubscriptionStoreContext implements SubscriptionStoreContext
{
	private SessionService sessionService;
	private BaseSiteService baseSiteService;
	private BaseStoreService baseStoreService;

	@Override
	public BaseStoreModel storeOf(final BillingSubscriptionRefModel subscription)
	{
		final AbstractOrderModel order = subscription == null ? null : subscription.getOrder();
		return order == null ? null : order.getStore();
	}

	@Override
	public <T> T callInStoreOf(final BillingSubscriptionRefModel subscription, final StoreBoundWork<T> work)
			throws BillingException
	{
		final AbstractOrderModel order = subscription == null ? null : subscription.getOrder();
		final BaseStoreModel store = order == null ? null : order.getStore();
		if (store == null)
		{
			throw new PreconditionFailedException("Subscription "
					+ (subscription == null ? "<null>" : subscription.getExternalSubscriptionId())
					+ " has no originating order with a base store; refusing to call its platform with another "
					+ "store's credentials");
		}

		final Outcome<T> outcome = sessionService.executeInLocalViewWithParams(
				Collections.singletonMap(SubscriptionBaseStoreSelectorStrategy.CURRENT_SUBSCRIPTION_BASE_STORE, store),
				new SessionExecutionBody()
				{
					@Override
					public Object execute()
					{
						try
						{
							establish(order, store);
							return new Outcome<>(work.call(store), null);
						}
						catch (final BillingException e)
						{
							// Carried out rather than wrapped, so callers keep its retryable/terminal type.
							return new Outcome<T>(null, e);
						}
					}
				});
		if (outcome.failure() != null)
		{
			throw outcome.failure();
		}
		return outcome.value();
	}

	@Override
	public void establish(final AbstractOrderModel order, final BaseStoreModel store) throws PreconditionFailedException
	{
		final BaseSiteModel site = order.getSite();
		if (site != null)
		{
			// Catalog versions are not needed to read connector credentials, and activating them is costly.
			baseSiteService.setCurrentBaseSite(site, false);
		}

		final BaseStoreModel resolved = baseStoreService.getCurrentBaseStore();
		if (resolved == null)
		{
			throw new PreconditionFailedException("Order '" + order.getCode() + "' resolves to no current base store"
					+ (site == null ? " because it has no base site" : " via base site '" + site.getUid()
							+ "', which lists no stores")
					+ "; refusing to call a billing platform without knowing whose credentials to use");
		}
		if (!Objects.equals(store.getPk(), resolved.getPk()))
		{
			throw new PreconditionFailedException("Order '" + order.getCode() + "' belongs to base store '"
					+ store.getUid() + "' but the session resolves to '" + resolved.getUid()
					+ "'; refusing, because the connector would use the wrong store's credentials and merchant account");
		}
	}

	private record Outcome<T>(T value, BillingException failure)
	{
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
