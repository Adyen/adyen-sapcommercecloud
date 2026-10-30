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
package com.adyen.commerce.connector.facades;

import com.adyen.commerce.connector.facades.data.PaymentMethodChangeReport;
import com.adyen.commerce.connector.facades.data.PaymentMethodChangeResult;
import com.adyen.commerce.connector.facades.data.SubscriptionOverviewData;

/**
 * The shopper's own view of their subscriptions, one section per billing platform, and what they may do to
 * them.
 *
 * <p>Every method is scoped to the customer in session and there is no overload that takes one. That is the
 * access control: a facade that accepted a customer would put the decision in the hands of each caller, and
 * one of those callers is a web tier where the identity in the path is attacker-controlled until a filter
 * has vouched for it.</p>
 *
 * <p>Every action names the platform of the section it came from, and is refused when the subscription is
 * not on that platform, so a control of one section can never act on another's subscriptions.</p>
 */
public interface MySubscriptionsFacade
{
	/** Whether any billing platform connector is loaded; without one there is nothing to show or do. */
	boolean hasAvailablePlatforms();

	/**
	 * Everything to show on the page for the customer in session. Returns an empty overview — never null,
	 * never an exception — when there is no such customer, so an anonymous request renders an empty page
	 * rather than a stack trace. Subscriptions on a platform with no loaded connector are not listed.
	 */
	SubscriptionOverviewData getSubscriptionsForCurrentCustomer();

	/**
	 * The address of the platform's own payment-method page for the shopper who owns this subscription, or
	 * {@code null} when there is none to give — the code is not theirs or not on that platform, the platform
	 * hosts no such page, or building the address failed. Called when the shopper asks to go there, never
	 * while the page renders, because on some platforms the address is a credential.
	 */
	String paymentMethodEnrollmentUrlForCurrentCustomer(String platformCode, String subscriptionCode);

	/**
	 * Stops the named subscription at the end of the period the shopper has already paid for.
	 *
	 * @return whether it was cancelled. {@code false} covers every reason the answer is no, because telling
	 *         them apart on screen would tell an unauthenticated caller which codes exist
	 */
	boolean cancelForCurrentCustomer(String platformCode, String subscriptionCode);

	/**
	 * Points billing at a payment method the shopper holds: one the platform already has, or a card Adyen
	 * vaults for them, as the subscription's connector accepts. On a platform whose change is account-wide
	 * every subscription of that account follows; the result says which happened.
	 *
	 * @param paymentMethodId the option the shopper chose
	 */
	PaymentMethodChangeResult changePaymentMethodForCurrentCustomer(String platformCode, String subscriptionCode,
			String paymentMethodId);

	/**
	 * The same change, then the same method put behind every other subscription of the shopper on that
	 * platform and billing account. Each is a separate call and may refuse on its own, which is why the answer
	 * carries counts. Where the change is already account-wide there is nothing to fan out.
	 */
	PaymentMethodChangeReport changePaymentMethodForAllSubscriptions(String platformCode, String subscriptionCode,
			String paymentMethodId);
}
