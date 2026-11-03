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
package com.adyen.commerce.connector.webhook;

import com.adyen.commerce.connector.dto.NormalizedBillingEvent;
import com.adyen.commerce.connector.dto.RawWebhook;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.BillingException;

/**
 * Routes a raw inbound webhook to the owning connector for verification and normalization, then reconciles
 * SAP state from the normalized event. Signature verification and payload parsing stay connector-owned, so
 * the dispatcher holds no per-vendor logic; the HTTP endpoint in the web layer identifies the platform and
 * hands the raw body here.
 */
public interface SubscriptionBillingWebhookDispatcher
{
	/**
	 * @param raw the raw, unverified webhook
	 * @return the normalized event after reconciliation
	 */
	NormalizedBillingEvent dispatch(BillingPlatform platform, RawWebhook raw) throws BillingException;
}
