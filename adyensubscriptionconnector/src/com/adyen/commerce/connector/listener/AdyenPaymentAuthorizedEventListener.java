package com.adyen.commerce.connector.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adyen.commerce.connector.activation.SubscriptionOrderActivator;
import com.adyen.v6.event.AdyenPaymentAuthorizedEvent;

import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.servicelayer.event.impl.AbstractEventListener;

/**
 * Re-enters the idempotent subscription activation flow after 3DS/redirect authorization.
 *
 * <p>Nothing is reported from here: {@link SubscriptionOrderActivator#activateFor} never throws, and an
 * event listener's caller is the multicaster, which would only log an exception. The activator journals
 * every attempt as a {@code BillingActivationAttempt} before calling the platform, so a failure on this path
 * is retried and dead-lettered by {@code SubscriptionActivationRetryJob} under the same policy as every
 * other path. A handler local to this listener would produce a record that job does not read.</p>
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
			// The journal is keyed on the order, so an activation without one could not be recorded,
			// retried or found afterwards.
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
