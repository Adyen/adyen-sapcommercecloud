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
package com.adyen.commerce.connector.dto;

/**
 * When a cancellation takes effect. There is no default: the two values are different acts, not the same
 * act sooner or later, so every call site names its timing.
 */
public enum CancellationTiming
{
	/**
	 * Stop renewing, keep serving until the period the shopper has already paid for runs out. The only
	 * timing whose meaning is the same on every platform, so a caller can choose it without knowing which
	 * connector is active.
	 */
	AT_PERIOD_END,

	/**
	 * End the subscription now, forfeiting the remainder of the paid period. On Chargebee this is a
	 * cancellation with {@code cancel_option=immediately}; on Recurly it is a <em>terminate</em>, a different
	 * API verb that ends service at once, and neither adapter sends a refund or credit instruction, so the
	 * merchant account's own configuration decides what happens to the shopper's money.
	 *
	 * <p>Nothing in the core confines this to operator- and system-initiated cancellations; that guard
	 * belongs at the edge that knows who is asking, since forbidding a combination here would only push an
	 * honest caller into misreporting its {@link CancelReason}.</p>
	 */
	IMMEDIATELY
}
