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
	 * <p>Carries no {@code networkTransactionId}: the caller has not established that an authorisation
	 * stands behind this token, so a connector whose {@code capabilities().requiresNetworkTransactionId()}
	 * is set refuses a handle built this way. A caller that does hold one uses
	 * {@link #createForVaultedToken} instead. The card metadata comes from the vault listing rather than
	 * from a PaymentInfo, so it may be partial or {@code null}.</p>
	 *
	 * @param customer               the shopper who owns the token
	 * @param store                  the base store whose Adyen merchant account minted it
	 * @param storedPaymentMethodId  the Adyen {@code recurringDetailReference}
	 * @param cardMetadata           display metadata, or {@code null} when none is available
	 * @throws TokenContractException if the customer has no shopperReference or the store no merchant account
	 */
	AdyenTokenHandle createForStoredToken(CustomerModel customer, BaseStoreModel store,
			String storedPaymentMethodId, CardMetadata cardMetadata) throws TokenContractException;

	/**
	 * The same, for a vaulted token whose original authorisation Adyen still reports.
	 *
	 * <p>Separate from {@link #createForStoredToken} so that the network transaction id is supplied by a
	 * caller that actually read one, rather than defaulted: a fabricated or guessed value would be sent to
	 * a card scheme as the reference of a transaction that never happened. A blank one is kept blank, which
	 * leaves the handle exactly as unusable to an NTID-requiring platform as the other method's.</p>
	 *
	 * @param networkTransactionId Adyen's {@code networkTxReference} for the authorisation that vaulted
	 *                             this token, or {@code null} when Adyen reports none
	 */
	AdyenTokenHandle createForVaultedToken(CustomerModel customer, BaseStoreModel store,
			String storedPaymentMethodId, String networkTransactionId, CardMetadata cardMetadata)
			throws TokenContractException;
}
