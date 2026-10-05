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
package com.adyen.commerce.connector.readiness;

import java.util.List;

import com.adyen.commerce.connector.enums.BillingPlatform;

import de.hybris.platform.store.BaseStoreModel;

/**
 * Static check that a base store holds everything one platform's connector needs before the store may switch
 * to that platform. Connector extensions contribute their check to
 * {@code activeBillingPlatformValidateInterceptor.readinessChecks}.
 */
public interface BillingPlatformReadinessCheck
{
	/**
	 * @return the platform this check covers
	 */
	BillingPlatform platform();

	/** Human-readable names of settings the store lacks for this platform; empty when ready. No remote calls. */
	List<String> missingSettings(BaseStoreModel store);
}
