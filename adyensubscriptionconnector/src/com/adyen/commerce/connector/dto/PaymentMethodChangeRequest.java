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
 * Ask a platform to bill this subscription to a different payment method from now on.
 *
 * <p>{@code subscription} is required even where the scope is {@link PaymentMethodChangeScope#CUSTOMER} and
 * the platform will move everything the customer has: the scope is declared by the connector, never inferred
 * from which argument the caller left null.</p>
 *
 * <p>The choice says what the shopper picked in terms the platform can act on - a card Adyen holds for them,
 * or something the platform already has - and carries no PAN, in keeping with the SPI's token-only rule. The
 * caller has already established that it belongs to this shopper.</p>
 */
public record PaymentMethodChangeRequest(BillingCustomerRef customer,
                                         BillingSubscriptionRef subscription,
                                         PaymentMethodChoice choice,
                                         String idempotencyKey)
{
	public PaymentMethodChangeRequest
	{
		Dtos.requireValue(customer, "customer");
		Dtos.requireValue(subscription, "subscription");
		Dtos.requireValue(choice, "choice");
	}
}
