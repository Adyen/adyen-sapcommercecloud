package com.adyen.commerce.connector.context;

import de.hybris.platform.basecommerce.strategies.BaseStoreSelectorStrategy;
import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.store.BaseStoreModel;

/**
 * Selects the exact base store attached to the subscription's originating order while reconciliation runs.
 * Outside that local session context it returns {@code null}, allowing SAP's ordinary site-based selector to run.
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
