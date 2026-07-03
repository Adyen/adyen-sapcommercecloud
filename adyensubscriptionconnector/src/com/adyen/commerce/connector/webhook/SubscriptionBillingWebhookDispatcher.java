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
 * Routes a raw inbound webhook to the owning connector for verification + normalization, then
 * reconciles SAP state from the normalized event (design P1.10). Signature verification and payload
 * parsing stay connector-owned; the dispatcher itself contains no per-vendor logic.
 *
 * <p>The HTTP endpoint that receives the webhook (in the OCC/web layer) is expected to identify the
 * platform and hand the raw body to this dispatcher.</p>
 */
public interface SubscriptionBillingWebhookDispatcher
{
	/**
	 * @param platform the platform the webhook came from
	 * @param raw      the raw, unverified webhook
	 * @return the normalized event after reconciliation
	 */
	NormalizedBillingEvent dispatch(BillingPlatform platform, RawWebhook raw) throws BillingException;
}
