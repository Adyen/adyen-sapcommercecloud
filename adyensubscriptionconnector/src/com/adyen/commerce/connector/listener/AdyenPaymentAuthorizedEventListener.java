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
 * <h3>Where a failure here ends up</h3>
 * <p>Nowhere visible, if you only read this class: {@link SubscriptionOrderActivator#activateFor} never
 * throws and this listener has nobody to report to - an event listener's caller is the multicaster, which
 * would do nothing with an exception but log it. What makes that safe is that the activator journals every
 * attempt it makes as a {@code BillingActivationAttempt} before calling the platform, so a failure on this
 * path is retried by {@code SubscriptionActivationRetryJob} and dead-lettered by the same policy as every
 * other path. Deliberately no second mechanism here: an error handler local to this listener would produce
 * a record the retry job does not read.</p>
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
			// Said out loud rather than passed on as null: the journal is keyed on the order, so an
			// activation without one could not be recorded, retried or found afterwards either.
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
