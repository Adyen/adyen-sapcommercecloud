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
 * The shopper's own view of their subscriptions, and the one thing they may do to them.
 *
 * <p>Every method is scoped to the customer in session and there is no overload that takes one. That is the
 * access control: a facade that accepted a customer would put the decision in the hands of each caller, and
 * one of those callers is a web tier where the identity in the path is attacker-controlled until a filter
 * has vouched for it.</p>
 */
public interface MySubscriptionsFacade
{
	/**
	 * Everything to show on the page for the customer in session. Returns an empty overview — never null,
	 * never an exception — when there is no such customer, so an anonymous request renders an empty page
	 * rather than a stack trace.
	 */
	SubscriptionOverviewData getSubscriptionsForCurrentCustomer();

	/**
	 * Stops the named subscription at the end of the period the shopper has already paid for.
	 *
	 * <p>Never immediately: on one platform an immediate cancellation is a different API verb that ends
	 * service at once, and neither adapter sends an instruction about the money, so the shopper would lose
	 * access they had bought and get nothing back.</p>
	 *
	 * @param code the public identifier from the page
	 * @return whether it was cancelled. {@code false} covers every reason the answer is no — not this
	 *         customer's subscription, no longer in a state that can be cancelled, or the platform refused
	 *         — because telling them apart on screen would tell an unauthenticated caller which codes exist
	 */
	/**
	 * The address of the platform's own payment-method page for the shopper who owns this subscription, or
	 * {@code null} when there is none to give — the code is not theirs, their platform hosts no such page,
	 * or building the address failed.
	 *
	 * <p>Called when the shopper asks to go there, never while the page renders: on Recurly the address
	 * embeds a token that opens the account, so it is minted only for somebody who clicked.</p>
	 */
	String paymentMethodEnrollmentUrlForCurrentCustomer(String subscriptionCode);

	boolean cancelForCurrentCustomer(String code);

	/**
	 * Points the shopper's billing at a card they already have vaulted with Adyen. Proof of concept.
	 *
	 * <p>Not "change the card on this subscription": on Chargebee a payment source belongs to the
	 * <em>customer</em> and the import replaces their primary one, so every subscription of theirs that
	 * bills against the primary follows. The subscription code establishes which customer and which store
	 * are meant, and refuses the request when the row is not theirs.</p>
	 *
	 * <p>It adds no new card. Minting one would need zero-auth, and this integration's zero-auth carries no
	 * 3DS plumbing, so a card requiring authentication could not be stored at all. A platform that cannot
	 * do the change answers {@code NOT_SUPPORTED_HERE} rather than a failure the shopper may retry.</p>
	 *
	 * @param subscriptionCode      the public identifier, used to establish ownership and the store
	 * @param storedPaymentMethodId the Adyen {@code recurringDetailReference} the shopper chose
	 * @return what happened, in the terms the page has to describe it
	 */
	PaymentMethodChangeResult changePaymentMethodForCurrentCustomer(String subscriptionCode,
			String storedPaymentMethodId);

	/**
	 * The same change, then the same card put behind every other subscription this shopper has on that
	 * platform and billing account.
	 *
	 * <p>A fan-out rather than one account-level call, because a platform that pins a method per
	 * subscription does not move a pinned one when its account default changes - so nothing but touching
	 * each subscription actually moves them all. Each is a separate call and may refuse on its own, which
	 * is why the answer carries counts.</p>
	 *
	 * <p>On a platform whose change is already customer-scoped there is nothing to fan out: the first call
	 * moved everything, and the report says so.</p>
	 */
	PaymentMethodChangeReport changePaymentMethodForAllSubscriptions(String subscriptionCode,
			String storedPaymentMethodId);
}
