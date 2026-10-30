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
package com.adyen.commerce.connector.selfservice;

/**
 * Where one subscription stands from the shopper's point of view, and what they may still do to it.
 *
 * <p>Deliberately not the normalized platform status: a subscription the shopper has already stopped stays
 * {@code ACTIVE} on the platform until its term ends, and one no platform read has confirmed is
 * {@code PENDING} whether it is about to start or was created a second ago.</p>
 */
public enum SubscriptionLifecycleState
{
	/** No platform has confirmed this subscription yet: nothing dependable to act on. */
	SETTING_UP(false, false),

	/** Running and will renew. */
	ACTIVE(true, true),

	/** Will start on a future date and has not begun billing. */
	STARTING_SOON(true, true),

	/** Still serving, but the platform has been told not to renew it. */
	ENDING(false, true),

	/** Not being served and not being billed; only an operator can resume it. */
	PAUSED(false, true),

	/** A payment did not go through and the platform is retrying. */
	PAST_DUE(true, true),

	/** Already set to end, and carrying an unpaid invoice as well. */
	PAST_DUE_ENDING(false, true),

	/** Over. */
	ENDED(false, false),

	/**
	 * The platform reported a value this integration has no meaning for, or none at all. Nothing is offered:
	 * acting on it would be acting on a guess.
	 */
	UNAVAILABLE(false, false);

	private final boolean cancellable;
	private final boolean paymentMethodChangeable;

	SubscriptionLifecycleState(final boolean cancellable, final boolean paymentMethodChangeable)
	{
		this.cancellable = cancellable;
		this.paymentMethodChangeable = paymentMethodChangeable;
	}

	/** Whether the shopper may stop this subscription themselves. */
	public boolean isCancellable()
	{
		return cancellable;
	}

	/**
	 * Whether pointing this subscription's billing at another payment method could still achieve anything.
	 * Wider than {@link #isCancellable()}: {@code ENDING}, {@code PAST_DUE_ENDING} and {@code PAUSED} are
	 * still being collected or can be resumed, so a working card matters.
	 */
	public boolean isPaymentMethodChangeable()
	{
		return paymentMethodChangeable;
	}
}
