package com.adyen.commerce.connector.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adyen.commerce.connector.activation.SubscriptionOrderActivator;
import com.adyen.v6.event.AdyenPaymentAuthorizedEvent;

import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.servicelayer.event.impl.AbstractEventListener;

/**
 * Re-enters the idempotent subscription activation after a 3DS or redirect authorization. The activator
 * never throws and journals its own failures for the retry job.
 */
public class AdyenPaymentAuthorizedEventListener extends AbstractEventListener<AdyenPaymentAuthorizedEvent>
{
	private static final Logger LOG = LoggerFactory.getLogger(AdyenPaymentAuthorizedEventListener.class);

	private SubscriptionOrderActivator subscriptionOrderActivator;

	@Override
	protected void onEvent(final AdyenPaymentAuthorizedEvent event)
	{
		final OrderModel order = event == null ? null : event.getOrder();
		if (order == null)
		{
			LOG.warn("An Adyen payment authorization arrived without an order; not activating a subscription.");
			return;
		}

		subscriptionOrderActivator.activateFor(order);
	}

	public void setSubscriptionOrderActivator(final SubscriptionOrderActivator subscriptionOrderActivator)
	{
		this.subscriptionOrderActivator = subscriptionOrderActivator;
	}
}
