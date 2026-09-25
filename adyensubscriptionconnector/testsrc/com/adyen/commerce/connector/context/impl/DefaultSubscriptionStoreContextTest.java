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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.adyen.commerce.connector.context.SubscriptionBaseStoreSelectorStrategy;
import com.adyen.commerce.connector.exception.PreconditionFailedException;
import com.adyen.commerce.connector.exception.RetryableBillingException;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.basecommerce.model.site.BaseSiteModel;
import de.hybris.platform.core.PK;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.servicelayer.session.SessionExecutionBody;
import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.site.BaseSiteService;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;

@UnitTest
public class DefaultSubscriptionStoreContextTest
{
	private SessionService sessionService;
	private BaseSiteService baseSiteService;
	private BaseStoreService baseStoreService;
	private BillingSubscriptionRefModel subscription;
	private AbstractOrderModel order;
	private BaseStoreModel store;
	private BaseSiteModel site;
	private DefaultSubscriptionStoreContext context;

	/** What the local view was opened with; the base store service answers from it, as the selector does. */
	private final Map<String, Object> localView = new HashMap<>();

	@Before
	public void setUp()
	{
		sessionService = mock(SessionService.class);
		baseSiteService = mock(BaseSiteService.class);
		baseStoreService = mock(BaseStoreService.class);
		subscription = mock(BillingSubscriptionRefModel.class);
		order = mock(AbstractOrderModel.class);
		store = mock(BaseStoreModel.class);
		site = mock(BaseSiteModel.class);

		context = new DefaultSubscriptionStoreContext();
		context.setSessionService(sessionService);
		context.setBaseSiteService(baseSiteService);
		context.setBaseStoreService(baseStoreService);

		when(subscription.getOrder()).thenReturn(order);
		when(subscription.getExternalSubscriptionId()).thenReturn("sub-1");
		when(order.getCode()).thenReturn("order-1");
		when(order.getStore()).thenReturn(store);
		when(order.getSite()).thenReturn(site);
		when(store.getPk()).thenReturn(PK.fromLong(1L));
		when(store.getUid()).thenReturn("electronics");

		when(sessionService.executeInLocalViewWithParams(anyMap(), any(SessionExecutionBody.class)))
				.thenAnswer(invocation -> {
					localView.putAll(invocation.getArgument(0));
					try
					{
						return invocation.getArgument(1, SessionExecutionBody.class).execute();
					}
					finally
					{
						localView.clear();
					}
				});
		when(baseStoreService.getCurrentBaseStore()).thenAnswer(invocation -> localView
				.get(SubscriptionBaseStoreSelectorStrategy.CURRENT_SUBSCRIPTION_BASE_STORE));
	}

	@Test
	public void runsTheWorkWithTheSubscriptionsOwnStoreAndSite() throws Exception
	{
		final Object result = context.callInStoreOf(subscription, given -> {
			assertSame(store, given);
			assertSame(store, baseStoreService.getCurrentBaseStore());
			return "done";
		});

		assertEquals("done", result);
		verify(baseSiteService).setCurrentBaseSite(site, false);
	}

	@Test
	public void refusesASubscriptionWithoutAnOrderBeforeOpeningAView()
	{
		when(subscription.getOrder()).thenReturn(null);

		assertThrows(PreconditionFailedException.class, () -> context.callInStoreOf(subscription, given -> {
			throw new AssertionError("the work must not run");
		}));
		verifyNoInteractions(sessionService);
	}

	@Test
	public void refusesAnOrderWithoutAStore()
	{
		when(order.getStore()).thenReturn(null);

		assertThrows(PreconditionFailedException.class, () -> context.callInStoreOf(subscription, given -> {
			throw new AssertionError("the work must not run");
		}));
		verifyNoInteractions(sessionService);
	}

	/** A session that resolves to another store means the selector is not wired in; the work must not run. */
	@Test
	public void refusesWhenTheSessionResolvesToAnotherStore()
	{
		final BaseStoreModel other = mock(BaseStoreModel.class);
		when(other.getPk()).thenReturn(PK.fromLong(2L));
		when(other.getUid()).thenReturn("apparel");
		when(baseStoreService.getCurrentBaseStore()).thenReturn(other);

		assertThrows(PreconditionFailedException.class, () -> context.callInStoreOf(subscription, given -> {
			throw new AssertionError("the work must not run");
		}));
	}

	@Test
	public void refusesWhenNoStoreResolvesAtAll()
	{
		when(baseStoreService.getCurrentBaseStore()).thenReturn(null);

		assertThrows(PreconditionFailedException.class, () -> context.callInStoreOf(subscription, given -> {
			throw new AssertionError("the work must not run");
		}));
	}

	/** The same exception instance, so callers still see whether it is retryable. */
	@Test
	public void carriesTheBillingExceptionOutUnchanged()
	{
		final RetryableBillingException failure = new RetryableBillingException("platform is down");

		final RetryableBillingException thrown = assertThrows(RetryableBillingException.class,
				() -> context.callInStoreOf(subscription, given -> {
					throw failure;
				}));

		assertSame(failure, thrown);
	}

	@Test
	public void theStoreOfASubscriptionIsItsOrdersStore()
	{
		assertSame(store, context.storeOf(subscription));
		when(subscription.getOrder()).thenReturn(null);
		assertEquals(null, context.storeOf(subscription));
	}
}
