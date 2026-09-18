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
package com.adyen.commerce.connector.facades.data;

/**
 * What a shopper is told about one subscription, and whether they are offered a way to stop it.
 *
 * <p>Deliberately not the normalized platform status: a subscription the shopper has already stopped stays
 * {@code ACTIVE} on both platforms until its term ends, and one no platform read has confirmed is
 * {@code PENDING} whether it is about to start or was created a second ago.</p>
 *
 * <p>Whether a date is shown is not part of the state — the period end is only known after the first
 * reconciliation — so the date travels separately on {@link SubscriptionEntryData} and the wording is chosen
 * from its presence.</p>
 */
public enum SubscriptionDisplayState
{
	/**
	 * No platform has confirmed this subscription yet: nothing to report and nothing dependable to cancel.
	 */
	SETTING_UP(false, false),

	/** Running and will renew. */
	ACTIVE(true, true),

	/** Will start on a future date and has not begun billing. */
	STARTING_SOON(true, true),

	/** Still serving, but the platform has been told not to renew it. */
	ENDING(false, true),

	/** Not being served and not being billed; only an operator can resume it on either platform. */
	PAUSED(false, true),

	/** A payment did not go through and the platform is retrying; the state shoppers most often act from. */
	PAST_DUE(true, true),

	/** Already set to end, and carrying an unpaid invoice as well. */
	PAST_DUE_ENDING(false, true),

	/** Over. */
	ENDED(false, false),

	/**
	 * The platform sent a value this integration has no sentence for, or none at all. Shown rather than
	 * hidden, because a subscription that is billing somebody must appear on their page, but never with a
	 * button: acting on it would be acting on a guess.
	 */
	UNAVAILABLE(false, false);

	private final boolean cancellable;
	private final boolean paymentMethodChangeable;

	SubscriptionDisplayState(final boolean cancellable, final boolean paymentMethodChangeable)
	{
		this.cancellable = cancellable;
		this.paymentMethodChangeable = paymentMethodChangeable;
	}

	/**
	 * Whether the shopper is offered a way to stop this subscription themselves. Read by the page to decide
	 * whether to render the button and by the facade to decide whether to honour a request that arrives
	 * anyway, since a form can be replayed after the state has moved on.
	 */
	public boolean isCancellable()
	{
		return cancellable;
	}

	/**
	 * Whether pointing this subscription's billing at a different payment method could still achieve
	 * anything. Read by the page to decide whether to offer the control and by the facade to decide whether
	 * to honour a request that arrives anyway.
	 *
	 * <p>Wider than {@link #isCancellable()}: {@code ENDING}, {@code PAST_DUE_ENDING} and {@code PAUSED} are
	 * still being collected or can be resumed, so a working card matters. False for {@code SETTING_UP},
	 * {@code ENDED} and {@code UNAVAILABLE}, where there is nothing confirmed to move.</p>
	 */
	public boolean isPaymentMethodChangeable()
	{
		return paymentMethodChangeable;
	}

	/**
	 * How loudly the page should carry this state: {@code attention}, {@code quiet} or {@code normal}.
	 *
	 * <p>An exhaustive switch here rather than a lookup in the view, so a new state is a compile error for
	 * whoever adds it instead of silently falling into a default bucket. The tone only reinforces the
	 * sentence and never carries meaning on its own.</p>
	 */
	public String getTone()
	{
		return switch (this)
		{
			case PAST_DUE, PAST_DUE_ENDING -> "attention";
			case SETTING_UP, ENDED, UNAVAILABLE -> "quiet";
			case ACTIVE, STARTING_SOON, ENDING, PAUSED -> "normal";
		};
	}
}
