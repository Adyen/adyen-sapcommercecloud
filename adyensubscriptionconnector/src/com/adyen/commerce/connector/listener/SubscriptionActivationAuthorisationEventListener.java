/*
 *                        ######
 *                        ######
 *  ############    ####( ######  #####. ######  ############   ############
 *  #############  #####( ######  #####. ######  #####  ######  #####  ######
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

import org.apache.commons.lang3.BooleanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;

import com.adyen.commerce.connector.activation.SubscriptionOrderActivator;
import com.adyen.v6.events.AuthorisationEvent;
import com.adyen.v6.model.AdyenNotificationModel;
import com.adyen.v6.repository.OrderRepository;

import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.servicelayer.event.impl.AbstractEventListener;

/**
 * Activates a subscription once Adyen confirms the authorisation, which is when the token and its network
 * transaction id reach the order's PaymentInfo.
 *
 * <p>A separate listener, because the core authorisation listener skips events whose payment transaction
 * already exists, as every card checkout's does. Lowest precedence, so the core listener runs first. It may
 * fire once per partial-payment leg or redelivery; the activator is idempotent.</p>
 */
public class SubscriptionActivationAuthorisationEventListener extends AbstractEventListener<AuthorisationEvent>
		implements Ordered
{
	private static final Logger LOG =
			LoggerFactory.getLogger(SubscriptionActivationAuthorisationEventListener.class);

	private SubscriptionOrderActivator subscriptionOrderActivator;
	private OrderRepository orderRepository;

	@Override
	protected void onEvent(final AuthorisationEvent event)
	{
		final AdyenNotificationModel notification = event == null ? null : event.getNotificationRequestItem();
		if (notification == null || !BooleanUtils.isTrue(notification.getSuccess()))
		{
			return;
		}

		// merchantReference is the order code: the order keeps the cart code it paid under.
		final String orderCode = notification.getMerchantReference();
		final OrderModel order = orderCode == null ? null : findOrder(orderCode);
		if (order == null)
		{
			// Expected for a partial payment's gift-card leg, authorised before the order exists.
			LOG.debug("No order '{}' for authorisation {}; not activating a subscription.", orderCode,
					notification.getPspReference());
			return;
		}

		subscriptionOrderActivator.activateFor(order);
	}

	/** Test seam: OrderRepository cannot be mocked outside a booted platform. */
	protected OrderModel findOrder(final String orderCode)
	{
		return orderRepository.getOrderModel(orderCode);
	}

	@Override
	public int getOrder()
	{
		return Ordered.LOWEST_PRECEDENCE;
	}

	public void setSubscriptionOrderActivator(final SubscriptionOrderActivator subscriptionOrderActivator)
	{
		this.subscriptionOrderActivator = subscriptionOrderActivator;
	}

	public void setOrderRepository(final OrderRepository orderRepository)
	{
		this.orderRepository = orderRepository;
	}
}
