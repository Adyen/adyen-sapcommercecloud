package com.adyen.commerce.connector.listener;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.Before;
import org.junit.Test;

import com.adyen.commerce.connector.activation.SubscriptionOrderActivator;
import com.adyen.v6.event.AdyenPaymentAuthorizedEvent;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.model.order.OrderModel;

@UnitTest
public class AdyenPaymentAuthorizedEventListenerTest
{
	private SubscriptionOrderActivator activator;
	private AdyenPaymentAuthorizedEventListener listener;

	@Before
	public void setUp()
	{
		activator = mock(SubscriptionOrderActivator.class);
		listener = new AdyenPaymentAuthorizedEventListener();
		listener.setSubscriptionOrderActivator(activator);
	}

	@Test
	public void reentersIdempotentActivationForAuthorizedOrder()
	{
		final OrderModel order = mock(OrderModel.class);

		listener.onEvent(new AdyenPaymentAuthorizedEvent(order));

		// The activator journals the attempt itself, so a failure from here is retried and dead-lettered
		// like any other; there is deliberately nothing to verify about error handling in this class.
		verify(activator).activateFor(order);
	}

	@Test
	public void ignoresAnAuthorizationWithoutAnOrder()
	{
		listener.onEvent(new AdyenPaymentAuthorizedEvent(null));

		verifyNoInteractions(activator);
	}

	@Test
	public void ignoresANullEvent()
	{
		listener.onEvent(null);

		verifyNoInteractions(activator);
	}
}
