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
 * act sooner or later, and which one a caller means has to be stated.
 *
 * <p>This exists because the choice used to be a bare {@code false} in the core service, which is not a
 * decision anyone made — it is a literal nobody had to defend. Every cancellation now names its timing at
 * the call site.</p>
 */
public enum CancellationTiming
{
	/**
	 * Stop renewing, keep serving until the period the shopper has already paid for runs out.
	 *
	 * <p>The right answer for anything a shopper asks for themselves. It is also the only timing whose
	 * meaning is the same on every platform, so it is the one a caller can choose without knowing which
	 * connector is active.</p>
	 */
	AT_PERIOD_END,

	/**
	 * End the subscription now, forfeiting the remainder of the paid period.
	 *
	 * <p>Deliberately not "the same thing, earlier". On Chargebee this is a cancellation with
	 * {@code cancel_option=immediately}; on Recurly it is a <em>terminate</em> — a different API verb that
	 * ends service at once and opens the question of a refund, which neither adapter currently answers
	 * (neither sends a refund or credit instruction, so the merchant account's own configuration decides
	 * what happens to the shopper's money).</p>
	 *
	 * <p>So this belongs to operator- and system-initiated cancellations — fraud, a failed migration, a
	 * dunning process that has run out — and not to a self-service button. Nothing in the core enforces
	 * that; the guard belongs at the edge that knows who is asking, not here, where forbidding a
	 * combination would only push an honest caller into misreporting its {@link CancelReason}.</p>
	 */
	IMMEDIATELY
}
