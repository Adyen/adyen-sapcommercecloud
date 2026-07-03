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
package com.adyen.commerce.connector.token;

import com.adyen.commerce.connector.dto.AdyenTokenHandle;
import com.adyen.commerce.connector.exception.TokenContractException;

import de.hybris.platform.core.model.order.AbstractOrderModel;

/**
 * Builds the uniform {@link AdyenTokenHandle} from the artifacts the existing Adyen plugin produces
 * (design P1.8). The plugin captures the token during checkout; this factory only reads it &mdash; no
 * PAN is ever touched.
 */
public interface AdyenTokenHandleFactory
{
	/**
	 * Assemble the token contract from a tokenized order.
	 *
	 * @throws TokenContractException if the order is missing the data required to charge the token
	 *                                (no payment info, no {@code adyenSelectedReference}, no shopper)
	 */
	AdyenTokenHandle create(AbstractOrderModel order) throws TokenContractException;
}
