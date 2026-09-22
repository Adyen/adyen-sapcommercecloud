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
 * The uniform token contract. Every supported platform can charge an Adyen-vaulted token expressed as
 * {@code shopperReference} + {@code storedPaymentMethodId} (== {@code recurringDetailReference}),
 * provided the platform is connected to the same Adyen merchant account. The network transaction id is
 * optional here and required by some connectors (Recurly); the card metadata is non-PCI, and no PAN ever
 * crosses this boundary.
 */
public record AdyenTokenHandle(String merchantAccount,
                               String shopperReference,
                               String storedPaymentMethodId,
                               String networkTransactionId,
                               CardMetadata cardMetadata)
{
	public AdyenTokenHandle
	{
		Dtos.requireText(merchantAccount, "merchantAccount");
		Dtos.requireText(shopperReference, "shopperReference");
		Dtos.requireText(storedPaymentMethodId, "storedPaymentMethodId");
	}

	public boolean hasNetworkTransactionId()
	{
		return networkTransactionId != null && !networkTransactionId.isBlank();
	}
}
