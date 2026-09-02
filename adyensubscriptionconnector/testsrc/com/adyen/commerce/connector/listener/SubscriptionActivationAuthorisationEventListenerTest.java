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
package com.adyen.commerce.connector.listener;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.Ordered;

import com.adyen.commerce.connector.activation.SubscriptionOrderActivator;
import com.adyen.v6.events.AuthorisationEvent;
import com.adyen.v6.model.AdyenNotificationModel;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.model.order.OrderModel;

/**
 * Unit test for {@link SubscriptionActivationAuthorisationEventListener} — which notifications lead to an
 * activation attempt, and which are ignored without touching the order at all.
 */
@UnitTest
public class SubscriptionActivationAuthorisationEventListenerTest
{
	private static final String ORDER_CODE = "order-1";

	@Mock
	private SubscriptionOrderActivator subscriptionOrderActivator;
	@Mock
	private AuthorisationEvent event;
	@Mock
	private AdyenNotificationModel notification;
	@Mock
	private OrderModel order;

	private SubscriptionActivationAuthorisationEventListener listener;
	private OrderModel foundOrder;
	private String lookedUp;

	@Before
	public void setUp()
	{
		MockitoAnnotations.openMocks(this);

		// Subclassed rather than injected with a mocked OrderRepository: that class holds a log4j 1.x logger
		// in a static initializer, so it cannot be instrumented outside a booted platform.
		listener = new SubscriptionActivationAuthorisationEventListener()
		{
			@Override
			protected OrderModel findOrder(final String orderCode)
			{
				lookedUp = orderCode;
				return ORDER_CODE.equals(orderCode) ? foundOrder : null;
			}
		};
		listener.setSubscriptionOrderActivator(subscriptionOrderActivator);
		foundOrder = order;

		when(event.getNotificationRequestItem()).thenReturn(notification);
		when(notification.getSuccess()).thenReturn(Boolean.TRUE);
		when(notification.getMerchantReference()).thenReturn(ORDER_CODE);
	}

	@Test
	public void activatesOnASuccessfulAuthorisation()
	{
		listener.onEvent(event);

		verify(subscriptionOrderActivator).activateFor(order);
	}

	/**
	 * A refusal must not create a subscription — the shopper was never charged.
	 */
	@Test
	public void ignoresARefusedAuthorisation()
	{
		when(notification.getSuccess()).thenReturn(Boolean.FALSE);

		listener.onEvent(event);

		verifyNoInteractions(subscriptionOrderActivator);
		assertNull("a refusal must not even look the order up", lookedUp);
	}

	@Test
	public void ignoresANotificationWithNoSuccessFlag()
	{
		when(notification.getSuccess()).thenReturn(null);

		listener.onEvent(event);

		verifyNoInteractions(subscriptionOrderActivator);
	}

	/**
	 * A partial payment authorises the gift-card leg against the cart, before any order exists. That
	 * notification carries the same reference and simply has nothing to activate yet.
	 */
	@Test
	public void ignoresANotificationWhoseOrderDoesNotExistYet()
	{
		foundOrder = null;

		listener.onEvent(event);

		verifyNoInteractions(subscriptionOrderActivator);
	}

	@Test
	public void ignoresANotificationWithoutAMerchantReference()
	{
		when(notification.getMerchantReference()).thenReturn(null);

		listener.onEvent(event);

		verifyNoInteractions(subscriptionOrderActivator);
	}

	@Test
	public void ignoresAnEventWithoutANotification()
	{
		when(event.getNotificationRequestItem()).thenReturn(null);

		listener.onEvent(event);

		verifyNoInteractions(subscriptionOrderActivator);
	}

	@Test
	public void ignoresANullEvent()
	{
		listener.onEvent(null);

		verifyNoInteractions(subscriptionOrderActivator);
	}

	/**
	 * Adyen redelivers, and a partial payment sends one notification per leg, so the same order can arrive
	 * more than once. The listener does not deduplicate — the activator is idempotent per order and owns
	 * that — but it must keep handing the order over rather than guessing.
	 */
	@Test
	public void handsTheSameOrderOverAgainOnARedelivery()
	{
		listener.onEvent(event);
		listener.onEvent(event);

		verify(subscriptionOrderActivator, times(2)).activateFor(order);
	}

	/**
	 * The core listener settles the payment transaction on the same event; this one must not run first.
	 */
	@Test
	public void runsAfterTheCorePaymentListener()
	{
		assertEquals(Ordered.LOWEST_PRECEDENCE, listener.getOrder());
	}
}
