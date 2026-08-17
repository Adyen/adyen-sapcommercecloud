package com.adyen.commerce.connector.listener;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.Test;

import com.adyen.commerce.connector.hook.impl.DefaultSubscriptionActivationPlaceOrderMethodHook;
import com.adyen.v6.event.AdyenPaymentAuthorizedEvent;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.model.order.OrderModel;

@UnitTest
public class AdyenPaymentAuthorizedEventListenerTest
{
	@Test
	public void reentersIdempotentActivationForAuthorizedOrder()
	{
		final DefaultSubscriptionActivationPlaceOrderMethodHook hook =
				mock(DefaultSubscriptionActivationPlaceOrderMethodHook.class);
		final OrderModel order = mock(OrderModel.class);
		final AdyenPaymentAuthorizedEventListener listener = new AdyenPaymentAuthorizedEventListener();
		listener.setActivationHook(hook);

		listener.onEvent(new AdyenPaymentAuthorizedEvent(order));

		verify(hook).activateOrder(order);
	}
}
