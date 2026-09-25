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
 * What came of a shopper asking to change the card their subscriptions are billed to.
 *
 * <p>Success is split by scope because "this subscription" and "all your subscriptions" are different
 * promises, and the page must make the one that actually happened. A platform that can never do this is
 * answered with {@code NOT_SUPPORTED_HERE} rather than with a refusal that invites a retry which cannot
 * succeed.</p>
 *
 * <p>{@code FAILED} also covers every rejection - a code that is not this shopper's, a card that is not
 * theirs, a row too far gone to act on - because telling those apart on screen would tell somebody probing
 * which subscription codes exist.</p>
 */
public enum PaymentMethodChangeResult
{
	CHANGED_THIS_SUBSCRIPTION,
	CHANGED_ALL_SUBSCRIPTIONS,
	NOT_SUPPORTED_HERE,
	FAILED
}
