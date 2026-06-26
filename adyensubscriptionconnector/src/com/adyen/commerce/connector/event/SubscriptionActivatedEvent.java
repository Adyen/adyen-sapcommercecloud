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
package com.adyen.commerce.connector.event;

import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;

import de.hybris.platform.servicelayer.event.events.AbstractEvent;

/**
 * Published by the orchestration service once a subscription has been activated on the platform and
 * its reference persisted. SAP-side listeners (fulfilment, notifications, analytics) can subscribe.
 */
public class SubscriptionActivatedEvent extends AbstractEvent
{
	private final transient BillingSubscriptionRefModel subscriptionRef;

	public SubscriptionActivatedEvent(final BillingSubscriptionRefModel subscriptionRef)
	{
		this.subscriptionRef = subscriptionRef;
	}

	public BillingSubscriptionRefModel getSubscriptionRef()
	{
		return subscriptionRef;
	}
}
