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

import com.adyen.commerce.connector.enums.BillingPlatform;

/**
 * Opaque reference to a payment source or billing-info on an external billing platform, returned by a
 * connector and persisted by the core as a {@code BillingPaymentMethodRefModel}.
 */
public record BillingPaymentMethodRef(BillingPlatform platform, String externalId)
{
	public BillingPaymentMethodRef
	{
		Dtos.requireValue(platform, "platform");
		Dtos.requireText(externalId, "externalId");
	}
}
