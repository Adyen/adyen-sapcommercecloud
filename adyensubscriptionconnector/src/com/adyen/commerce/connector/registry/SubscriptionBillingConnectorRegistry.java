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
package com.adyen.commerce.connector.registry;

import java.util.List;
import java.util.Optional;

import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.ConnectorNotConfiguredException;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;

import de.hybris.platform.store.BaseStoreModel;

/**
 * Resolves the {@link SubscriptionBillingConnector} to use. Adapter extensions contribute their
 * connector to the underlying list (Spring list-merge); adding a connector makes it resolvable with
 * no change to the core.
 */
public interface SubscriptionBillingConnectorRegistry
{
	/**
	 * @return the connector for the given platform
	 * @throws ConnectorNotConfiguredException if none is registered for it
	 */
	SubscriptionBillingConnector getConnector(BillingPlatform platform) throws ConnectorNotConfiguredException;

	/**
	 * Resolve the connector for the store's {@code activeBillingPlatform} (one active connector per
	 * BaseStore).
	 *
	 * @throws ConnectorNotConfiguredException if the store has no active platform or none is registered
	 */
	SubscriptionBillingConnector getActiveConnector(BaseStoreModel store) throws ConnectorNotConfiguredException;

	/**
	 * @return the connector for the platform, or empty if none is registered
	 */
	Optional<SubscriptionBillingConnector> findConnector(BillingPlatform platform);

	/**
	 * @return all registered connectors
	 */
	List<SubscriptionBillingConnector> getConnectors();
}
