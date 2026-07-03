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
package com.adyen.commerce.connector.validation;

import com.adyen.commerce.connector.exception.PreconditionFailedException;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;

import de.hybris.platform.store.BaseStoreModel;

/**
 * Enforces the universal precondition (design R2): the active connector's Adyen gateway must point at
 * the same Adyen merchant account as the store ({@code BaseStore.adyenMerchantAccount}). A mismatch
 * fails silently at charge time on the platform, so the core fails fast instead.
 */
public interface ConnectorMerchantAccountValidator
{
	/**
	 * @throws PreconditionFailedException if the connector's Adyen account does not match the store's
	 */
	void validate(SubscriptionBillingConnector connector, BaseStoreModel store) throws PreconditionFailedException;
}
