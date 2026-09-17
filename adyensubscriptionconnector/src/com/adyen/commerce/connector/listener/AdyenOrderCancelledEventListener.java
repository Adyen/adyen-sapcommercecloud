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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adyen.commerce.connector.activation.SubscriptionOrderCancellationService;
import com.adyen.v6.event.AdyenOrderCancelledEvent;

import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.servicelayer.event.impl.AbstractEventListener;

/**
 * Stops the subscription of an order that has been cancelled.
 *
 * <p>An event rather than a direct call because the dependency runs the wrong way: the extension that
 * cancels the order cannot see this one. Twin of {@link AdyenPaymentAuthorizedEventListener}, which starts
 * a subscription on the same terms.</p>
 */
public class AdyenOrderCancelledEventListener extends AbstractEventListener<AdyenOrderCancelledEvent>
{
	private static final Logger LOG = LoggerFactory.getLogger(AdyenOrderCancelledEventListener.class);

	private SubscriptionOrderCancellationService subscriptionOrderCancellationService;

	@Override
	protected void onEvent(final AdyenOrderCancelledEvent event)
	{
		final OrderModel order = event == null ? null : event.getOrder();
		if (order == null)
		{
			// Without an order there is no way to find which subscription was supposed to stop, which leaves a
			// shopper being billed for a cancelled order.
			LOG.warn("An order cancellation arrived without an order; no subscription can be stopped for it.");
			return;
		}

		subscriptionOrderCancellationService.cancelSubscriptionFor(order);
	}

	public void setSubscriptionOrderCancellationService(
			final SubscriptionOrderCancellationService subscriptionOrderCancellationService)
	{
		this.subscriptionOrderCancellationService = subscriptionOrderCancellationService;
	}
}
