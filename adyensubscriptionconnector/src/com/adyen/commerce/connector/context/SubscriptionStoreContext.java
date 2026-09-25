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
package com.adyen.commerce.connector.context;

import com.adyen.commerce.connector.exception.BillingException;
import com.adyen.commerce.connector.exception.PreconditionFailedException;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;

import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.store.BaseStoreModel;


/**
 * Runs connector calls against the base store that owns a subscription. Connectors read their credentials
 * from the current base store, so a call must never depend on whichever store the calling thread has.
 */
public interface SubscriptionStoreContext
{
	/**
	 * Work that needs the subscription's store in context.
	 *
	 * @param <T> what the work returns
	 */
	@FunctionalInterface
	interface StoreBoundWork<T>
	{
		T call(BaseStoreModel store) throws BillingException;
	}

	/**
	 * @return the store of the subscription's originating order, or {@code null} when there is none
	 */
	BaseStoreModel storeOf(BillingSubscriptionRefModel subscription);

	/**
	 * Runs the work in a local session view with the subscription's store and site in context.
	 *
	 * @throws PreconditionFailedException when the subscription has no order or store, or the session does not
	 *         resolve to that store; the work is not run
	 */
	<T> T callInStoreOf(BillingSubscriptionRefModel subscription, StoreBoundWork<T> work) throws BillingException;

	/**
	 * Activates the order's base site in the current session and checks that it resolves to {@code store}.
	 * Must run inside a local session view.
	 *
	 * @throws PreconditionFailedException when the session resolves to no store or to another one
	 */
	void establish(AbstractOrderModel order, BaseStoreModel store) throws PreconditionFailedException;
}
