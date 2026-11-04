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

import com.adyen.commerce.connector.selfservice.SubscriptionLifecycleState;

/**
 * What a shopper is told about one subscription: the lifecycle state, with how loudly the page carries it.
 *
 * <p>Whether a date is shown is not part of the state — the period end is only known after the first
 * reconciliation — so the date travels separately on {@link SubscriptionEntryData} and the wording is chosen
 * from its presence.</p>
 */
public enum SubscriptionDisplayState
{
	SETTING_UP(SubscriptionLifecycleState.SETTING_UP),
	ACTIVE(SubscriptionLifecycleState.ACTIVE),
	STARTING_SOON(SubscriptionLifecycleState.STARTING_SOON),
	ENDING(SubscriptionLifecycleState.ENDING),
	PAUSED(SubscriptionLifecycleState.PAUSED),
	PAST_DUE(SubscriptionLifecycleState.PAST_DUE),
	PAST_DUE_ENDING(SubscriptionLifecycleState.PAST_DUE_ENDING),
	ENDED(SubscriptionLifecycleState.ENDED),
	UNAVAILABLE(SubscriptionLifecycleState.UNAVAILABLE);

	private final SubscriptionLifecycleState lifecycleState;

	SubscriptionDisplayState(final SubscriptionLifecycleState lifecycleState)
	{
		this.lifecycleState = lifecycleState;
	}

	/** An exhaustive switch, so a new lifecycle state is a compile error here rather than a blank row. */
	public static SubscriptionDisplayState of(final SubscriptionLifecycleState state)
	{
		return switch (state)
		{
			case SETTING_UP -> SETTING_UP;
			case ACTIVE -> ACTIVE;
			case STARTING_SOON -> STARTING_SOON;
			case ENDING -> ENDING;
			case PAUSED -> PAUSED;
			case PAST_DUE -> PAST_DUE;
			case PAST_DUE_ENDING -> PAST_DUE_ENDING;
			case ENDED -> ENDED;
			case UNAVAILABLE -> UNAVAILABLE;
		};
	}

	/** Read by the page through EL, so a JavaBeans getter rather than a plain method. */
	public boolean isCancellable()
	{
		return lifecycleState.isCancellable();
	}

	public boolean isPaymentMethodChangeable()
	{
		return lifecycleState.isPaymentMethodChangeable();
	}

	/**
	 * How loudly the page should carry this state: {@code attention}, {@code quiet} or {@code normal}. The
	 * tone only reinforces the sentence and never carries meaning on its own.
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
