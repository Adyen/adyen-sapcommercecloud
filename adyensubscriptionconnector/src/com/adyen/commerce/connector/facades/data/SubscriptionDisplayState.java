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
 * <p>Deliberately not the normalized platform status. That vocabulary answers "what does the platform say",
 * which is a different question from "what should this person read": a subscription the shopper has already
 * stopped is still {@code ACTIVE} on both platforms and stays that way until the term ends, and one that has
 * never been confirmed by any platform read is {@code PENDING} whether it is about to start or was created
 * a second ago. Collapsing those into the platform's words would produce a page that is accurate and
 * useless.</p>
 *
 * <p>Whether a date is shown is not part of the state. Every state that can carry one may also be missing
 * it — the period end is only known after the first reconciliation — so the date travels separately on
 * {@link SubscriptionEntryData} and the wording is chosen from its presence. Encoding "with date" and
 * "without date" as separate states would double this enum to say the same thing twice.</p>
 */
public enum SubscriptionDisplayState
{
	/**
	 * No platform has confirmed this subscription yet. Never carries a date and never offers a button:
	 * there is nothing to tell the shopper and nothing dependable to cancel.
	 */
	SETTING_UP(false, false),

	/** Running and will renew. */
	ACTIVE(true, true),

	/** Will start on a future date and has not begun billing. Cancellable, and cheaply so. */
	STARTING_SOON(true, true),

	/** Still serving, but the platform has been told not to renew it. */
	ENDING(false, true),

	/** Not being served and not being billed; only an operator can resume it on either platform. */
	PAUSED(false, true),

	/** A payment did not go through and the platform is retrying. Still stoppable — and this is the state shoppers most often want to stop from. */
	PAST_DUE(true, true),

	/** Already set to end, and carrying an unpaid invoice as well. */
	PAST_DUE_ENDING(false, true),

	/** Over. */
	ENDED(false, false),

	/**
	 * The status could not be interpreted — the platform sent a value this integration has no sentence for,
	 * or none at all. Shown rather than hidden, because a subscription that is billing somebody must appear
	 * on their page even when we cannot describe it, but never with a button: acting on it would be acting
	 * on a guess.
	 *
	 * {@link com.adyen.commerce.connector.facades.data.SubscriptionEntryData#isManageable()}.</p>
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
	 * Whether the shopper is offered a way to stop this subscription themselves.
	 *
	 * <p>Read by the page to decide whether to render the button and by the facade to decide whether to
	 * honour a request that arrives anyway — a form can be replayed after the state has moved on.</p>
	 */
	public boolean isCancellable()
	{
		return cancellable;
	}

	/**
	 * Whether pointing this subscription's billing at a different payment method could still achieve
	 * anything.
	 *
	 * <p>Deliberately wider than {@link #isCancellable()}, and the two disagree in both directions.
	 * {@code ENDING} and {@code PAST_DUE_ENDING} can no longer be cancelled — they already are — yet the
	 * period the shopper has paid for is still being collected, so a working card still matters.
	 * {@code PAUSED} likewise: an operator can resume it, and a card fixed beforehand is the difference
	 * between resuming and dunning. {@code PAST_DUE} is the case this exists for.</p>
	 *
	 * <p>False where the answer would be a guess rather than a refusal: {@code SETTING_UP} has no confirmed
	 * subscription to move, {@code ENDED} has nothing left to bill, and {@code UNAVAILABLE} means we could
	 * not read the thing at all.</p>
	 *
	 * <p>Read by the page to decide whether to offer the control and by the facade to decide whether to
	 * honour a request that arrives anyway. The cancellation path already works this way; the payment-method
	 * path did not check at all, so a row that had ended accepted the request and sent a call that could not
	 * mean anything.</p>
	 */
	public boolean isPaymentMethodChangeable()
	{
		return paymentMethodChangeable;
	}

	/**
	 * How loudly the page should carry this state: {@code attention}, {@code quiet} or {@code normal}.
	 *
	 * <p>Here rather than as a lookup in the view, because a lookup keyed on nine constants is a second
	 * copy of this enum that drifts the first time somebody adds a tenth — silently, into whichever bucket
	 * the default happens to be. This way a new state is a compile-time decision by the person adding it.</p>
	 *
	 * <p>Three tones, not nine colours. {@code attention} is for the two states where the shopper's money is
	 * at risk and something can still be done about it; {@code quiet} is for the three that are over or not
	 * yet begun, which must be present but must not compete; everything else is ordinary. The tone only ever
	 * reinforces the sentence — it never carries meaning on its own, because a shopper who cannot see colour
	 * reads exactly the same page.</p>
	 */
	public String tone()
	{
		return switch (this)
		{
			case PAST_DUE, PAST_DUE_ENDING -> "attention";
			case SETTING_UP, ENDED, UNAVAILABLE -> "quiet";
			case ACTIVE, STARTING_SOON, ENDING, PAUSED -> "normal";
		};
	}
}
