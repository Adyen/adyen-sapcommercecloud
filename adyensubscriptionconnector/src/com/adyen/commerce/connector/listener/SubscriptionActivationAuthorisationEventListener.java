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
 * Activates a subscription once Adyen has confirmed the authorisation.
 *
 * <p>The Adyen token reaches the order's PaymentInfo only in
 * {@code DefaultAdyenOrderService.updatePaymentInfo}, which runs <em>after</em> the order is placed and, on
 * the 3DS path, only once the shopper returns — so the token and its network transaction id are available
 * here and not during checkout. Waiting for the notification also means a shopper who abandons the 3DS
 * challenge gets no live subscription off an unauthorised order.</p>
 *
 * <p>A separate listener rather than a hook inside {@code AuthorisationNotificationEventListener}: that
 * class's {@code processAuthorisationEvent} is guarded by "has a PaymentTransaction for this pspReference
 * already been created?", which is true for every ordinary card checkout. A second listener on the same
 * event runs regardless, and the platform isolates listener failures from each other.</p>
 *
 * <p>Runs at lowest precedence so the core listener has settled the payment transaction first. A partial
 * payment produces one notification per leg and Adyen may redeliver, so this can fire several times for one
 * order; {@link SubscriptionOrderActivator} is idempotent per order and absorbs that.</p>
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

		// merchantReference is the order code: the plugin overrides the create-order strategy so the order
		// keeps the cart code it paid under.
		final String orderCode = notification.getMerchantReference();
		final OrderModel order = orderCode == null ? null : findOrder(orderCode);
		if (order == null)
		{
			// Expected for a partial payment's gift-card leg, which is authorised against the cart before an
			// order exists. The card leg's notification arrives later and carries the same reference.
			LOG.debug("No order '{}' for authorisation {}; not activating a subscription.", orderCode,
					notification.getPspReference());
			return;
		}

		subscriptionOrderActivator.activateFor(order);
	}

	/**
	 * Seam over the plugin's own order lookup, which knows to ignore versioned copies. Kept separate so a
	 * unit test can supply an order without loading OrderRepository — that class holds a log4j 1.x logger in
	 * a static initializer and cannot be mocked outside a booted platform.
	 */
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
