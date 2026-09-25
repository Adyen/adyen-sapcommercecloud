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
package com.adyen.commerce.connector.exception;

/**
 * The plan resolver could not say whether a product is a subscription product &mdash; it failed rather
 * than answered.
 *
 * <p>Deliberately not a {@link PlanNotMappedException}: "no mapping exists" is an answer, and collapsing
 * the two lets a broken resolver turn a genuine subscription order into an ordinary one, silently.</p>
 *
 * <p>Retryable, because the failures that produce it are transient &mdash; a FlexibleSearch that timed
 * out, a connector whose configuration was not readable at that instant. The attempt cap is what stops a
 * permanent breakage from looping.</p>
 */
public class SubscriptionProductUndecidableException extends RetryableBillingException
{
	public SubscriptionProductUndecidableException(final String message, final Throwable cause)
	{
		super(message, cause);
	}
}
