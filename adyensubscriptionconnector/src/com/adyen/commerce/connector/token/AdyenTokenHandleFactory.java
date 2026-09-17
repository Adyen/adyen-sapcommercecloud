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
import com.adyen.commerce.connector.dto.CardMetadata;
import com.adyen.commerce.connector.exception.TokenContractException;

import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.store.BaseStoreModel;

/**
 * Builds the uniform {@link AdyenTokenHandle} from the artifacts the Adyen plugin produces. The plugin
 * captures the token during checkout; this factory only reads it, and no PAN is ever touched.
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

	/**
	 * Assemble the token contract for a token the shopper already has vaulted, outside any order — the case
	 * of pointing a running subscription at a different stored card.
	 *
	 * <p>Two pieces an order would carry are absent by nature. There is no {@code networkTransactionId},
	 * since no authorisation happened, so a connector whose
	 * {@code capabilities().requiresNetworkTransactionId()} is set refuses a handle built this way. And the
	 * card metadata comes from the vault listing rather than from a PaymentInfo, so it may be partial or
	 * {@code null}.</p>
	 *
	 * @param customer               the shopper who owns the token
	 * @param store                  the base store whose Adyen merchant account minted it
	 * @param storedPaymentMethodId  the Adyen {@code recurringDetailReference}
	 * @param cardMetadata           display metadata, or {@code null} when none is available
	 * @throws TokenContractException if the customer has no shopperReference or the store no merchant account
	 */
	AdyenTokenHandle createForStoredToken(CustomerModel customer, BaseStoreModel store,
			String storedPaymentMethodId, CardMetadata cardMetadata) throws TokenContractException;
}
