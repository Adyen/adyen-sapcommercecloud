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
 * Base, vendor-neutral checked exception for the connector boundary. Connectors translate every
 * platform/transport failure into a subtype of this so the core never sees a vendor exception.
 *
 * <p>The {@link #isRetryable()} flag drives the core's retry policy: transient failures
 * (timeouts, 5xx, rate limits) should be {@link RetryableBillingException}; everything that will
 * fail again on replay should be a terminal subtype.</p>
 */
public class BillingException extends Exception
{
	private final boolean retryable;

	public BillingException(final String message)
	{
		this(message, false, null);
	}

	public BillingException(final String message, final Throwable cause)
	{
		this(message, false, cause);
	}

	protected BillingException(final String message, final boolean retryable, final Throwable cause)
	{
		super(message, cause);
		this.retryable = retryable;
	}

	/**
	 * @return {@code true} if retrying the same operation may succeed.
	 */
	public boolean isRetryable()
	{
		return retryable;
	}
}
