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
package com.adyen.commerce.connector.duplicate;

import com.adyen.commerce.connector.enums.DuplicateSubscriptionRule;
import com.adyen.commerce.connector.selfservice.SubscriptionLifecycleState;

/**
 * The reason a purchase is refused. Carries no personal data, so it can go into a log line or an error body.
 *
 * @param source           what proved the customer already owns it
 * @param subscriptionCode the shopper-facing code of the subscription ({@code BillingSubscriptionRef.code}), or
 *                         {@code null} when there is none: a pending order, or a subscription only the platform knows
 * @param state            where that subscription stands; {@code SETTING_UP} for a pending order
 * @param rule             the rule that made it a duplicate
 */
public record BlockingSubscription(Source source, String subscriptionCode, SubscriptionLifecycleState state,
		DuplicateSubscriptionRule rule)
{
	/** What the block rests on. */
	public enum Source
	{
		/** A {@code BillingSubscriptionRef} this system holds. */
		EXISTING_SUBSCRIPTION,

		/** An order bought recently whose subscription has not been set up yet. */
		PENDING_ORDER,

		/** A subscription only the billing platform reported, in answer to a listing. */
		PLATFORM
	}
}
