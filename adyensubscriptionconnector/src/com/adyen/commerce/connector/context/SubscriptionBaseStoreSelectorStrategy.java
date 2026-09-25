package com.adyen.commerce.connector.context;

import de.hybris.platform.basecommerce.strategies.BaseStoreSelectorStrategy;
import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.store.BaseStoreModel;

/**
 * Selects the store of the subscription being worked on, set by {@code SubscriptionStoreContext} in a local
 * session view. Returns {@code null} elsewhere, so SAP's site-based selector runs.
 */
public class SubscriptionBaseStoreSelectorStrategy implements BaseStoreSelectorStrategy
{
	public static final String CURRENT_SUBSCRIPTION_BASE_STORE = "currentSubscriptionBaseStore";

	private SessionService sessionService;

	@Override
	public BaseStoreModel getCurrentBaseStore()
	{
		return sessionService.getAttribute(CURRENT_SUBSCRIPTION_BASE_STORE);
	}

	public void setSessionService(final SessionService sessionService)
	{
		this.sessionService = sessionService;
	}
}
