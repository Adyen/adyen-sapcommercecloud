package com.adyen.commerce.connector.listener;

import com.adyen.commerce.connector.activation.SubscriptionOrderActivator;
import com.adyen.v6.event.AdyenPaymentAuthorizedEvent;

import de.hybris.platform.servicelayer.event.impl.AbstractEventListener;

/** Re-enters the idempotent subscription activation flow after 3DS/redirect authorization. */
public class AdyenPaymentAuthorizedEventListener extends AbstractEventListener<AdyenPaymentAuthorizedEvent>
{
	private SubscriptionOrderActivator subscriptionOrderActivator;

	@Override
	protected void onEvent(final AdyenPaymentAuthorizedEvent event)
	{
		subscriptionOrderActivator.activateFor(event == null ? null : event.getOrder());
	}

	public void setSubscriptionOrderActivator(final SubscriptionOrderActivator subscriptionOrderActivator)
	{
		this.subscriptionOrderActivator = subscriptionOrderActivator;
	}
}
