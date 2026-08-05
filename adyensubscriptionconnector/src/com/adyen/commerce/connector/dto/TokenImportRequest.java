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
 * Request to import an Adyen-vaulted token onto the billing platform as a stored payment method.
 */
public record TokenImportRequest(BillingCustomerRef customer,
                                 AdyenTokenHandle token,
                                 RecurringProcessingModel model,
                                 BillingAddress billingAddress)
{
	public TokenImportRequest(final BillingCustomerRef customer,
	                          final AdyenTokenHandle token,
	                          final RecurringProcessingModel model)
	{
		this(customer, token, model, null);
	}

	public TokenImportRequest
	{
		Dtos.requireValue(customer, "customer");
		Dtos.requireValue(token, "token");
		Dtos.requireValue(model, "model");
	}
}
