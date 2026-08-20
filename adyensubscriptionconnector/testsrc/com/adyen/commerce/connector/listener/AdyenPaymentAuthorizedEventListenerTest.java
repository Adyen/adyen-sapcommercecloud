package com.adyen.commerce.connector.listener;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.Test;

import com.adyen.commerce.connector.activation.SubscriptionOrderActivator;
import com.adyen.v6.event.AdyenPaymentAuthorizedEvent;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.model.order.OrderModel;

@UnitTest
public class AdyenPaymentAuthorizedEventListenerTest
{
	@Test
	public void reentersIdempotentActivationForAuthorizedOrder()
	{
		final SubscriptionOrderActivator activator = mock(SubscriptionOrderActivator.class);
		final OrderModel order = mock(OrderModel.class);
		final AdyenPaymentAuthorizedEventListener listener = new AdyenPaymentAuthorizedEventListener();
		listener.setSubscriptionOrderActivator(activator);

		listener.onEvent(new AdyenPaymentAuthorizedEvent(order));

		verify(activator).activateFor(order);
	}
}
