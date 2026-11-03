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
 * How a platform expects the Adyen token pair (shopperReference + recurringDetailReference /
 * storedPaymentMethodId) to be expressed on import. Declared by each connector via
 * {@link ConnectorCapabilities} so the core never hard-codes per-platform shapes.
 *
 * <ul>
 *   <li>{@link #SEPARATE_FIELDS} &mdash; token + a separate account-reference field (Recurly).</li>
 *   <li>{@link #SLASH_JOINED} &mdash; {@code shopperReference/recurringDetailReference} as one value (Chargebee).</li>
 *   <li>{@link #DUAL_TOKEN} &mdash; two distinct token fields, e.g. tokenId + secondTokenId (Zuora).</li>
 * </ul>
 */
public enum TokenImportStyle
{
	SEPARATE_FIELDS,
	SLASH_JOINED,
	DUAL_TOKEN
}
