package com.adyen.commerce.connector.context;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.store.BaseStoreModel;

@UnitTest
public class SubscriptionBaseStoreSelectorStrategyTest
{
	@Mock
	private SessionService sessionService;
	@Mock
	private BaseStoreModel store;

	private SubscriptionBaseStoreSelectorStrategy strategy;

	@Before
	public void setUp()
	{
		MockitoAnnotations.openMocks(this);
		strategy = new SubscriptionBaseStoreSelectorStrategy();
		strategy.setSessionService(sessionService);
	}

	@Test
	public void returnsStoreFromReconciliationSession()
	{
		when(sessionService.getAttribute(SubscriptionBaseStoreSelectorStrategy.CURRENT_SUBSCRIPTION_BASE_STORE))
				.thenReturn(store);

		assertSame(store, strategy.getCurrentBaseStore());
	}
}
