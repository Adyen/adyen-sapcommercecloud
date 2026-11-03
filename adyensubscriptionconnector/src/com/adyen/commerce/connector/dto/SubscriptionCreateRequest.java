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

import java.time.Instant;
import java.util.Map;

/**
 * Request to create a subscription on the billing platform.
 *
 * @param customer       resolved external customer reference
 * @param paymentMethod  resolved external payment-method reference (the imported Adyen token)
 * @param plan           resolved platform plan/price reference
 * @param quantity       subscribed quantity
 * @param unitPrice       optional price override; {@code null} uses the plan price
 * @param currencyIsoCode optional ISO currency code; {@code null} uses the plan currency
 * @param cycle          optional billing cycle override; {@code null} uses the plan cycle
 * @param startDate      subscription start; honored as future-dated where the connector requires it
 * @param metadata       free-form metadata (e.g. SAP order code, cart id)
 * @param idempotencyKey caller-supplied idempotency key (e.g. SAP order code)
 */
public record SubscriptionCreateRequest(BillingCustomerRef customer,
                                        BillingPaymentMethodRef paymentMethod,
                                        PlanRef plan,
                                        int quantity,
                                        Money unitPrice,
                                        String currencyIsoCode,
                                        BillingCycle cycle,
                                        Instant startDate,
                                        Map<String, String> metadata,
                                        String idempotencyKey)
{
	public SubscriptionCreateRequest
	{
		Dtos.requireValue(customer, "customer");
		Dtos.requireValue(paymentMethod, "paymentMethod");
		Dtos.requireValue(plan, "plan");
		Dtos.requirePositive(quantity, "quantity");
		metadata = Dtos.immutableCopy(metadata);
	}
}
