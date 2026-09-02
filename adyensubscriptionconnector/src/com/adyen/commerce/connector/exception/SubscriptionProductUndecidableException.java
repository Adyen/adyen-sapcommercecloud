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
 * <p>Deliberately not a {@link PlanNotMappedException}. "No mapping exists" is an answer, and the answer
 * is no; this is the absence of one. Collapsing the two is what lets a broken resolver turn a genuine
 * subscription order into an ordinary one, silently, on both sides of the payment.</p>
 *
 * <p>Retryable, because the failures that produce it are the transient ones: a FlexibleSearch that
 * timed out, a connector whose configuration was not readable at that instant. The same leniency
 * {@code DefaultBillingRetryPolicy} already applies to unclassified failures applies here for the same
 * reason &mdash; the shopper has paid, and a bounded series of retries costs less than dead-lettering
 * the order straight away. The attempt cap is what stops a genuinely permanent breakage from looping.</p>
 */
public class SubscriptionProductUndecidableException extends RetryableBillingException
{
	public SubscriptionProductUndecidableException(final String message, final Throwable cause)
	{
		super(message, cause);
	}
}
