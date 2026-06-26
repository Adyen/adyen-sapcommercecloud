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
 * The uniform token contract &mdash; the heart of the abstraction.
 *
 * <p>All supported platforms can charge an Adyen-vaulted token expressed as
 * {@code shopperReference} + {@code storedPaymentMethodId} (== {@code recurringDetailReference}),
 * provided the platform is connected to the same Adyen merchant account. This record carries
 * exactly that, plus an optional network transaction id (required by some connectors, e.g. Recurly)
 * and non-PCI card metadata. No PAN ever crosses this boundary.</p>
 */
public record AdyenTokenHandle(String merchantAccount,
                               String shopperReference,
                               String storedPaymentMethodId,
                               String networkTransactionId,
                               CardMetadata cardMetadata)
{
	/**
	 * @return {@code true} if a non-blank network transaction id is present.
	 */
	public boolean hasNetworkTransactionId()
	{
		return networkTransactionId != null && !networkTransactionId.isBlank();
	}
}
