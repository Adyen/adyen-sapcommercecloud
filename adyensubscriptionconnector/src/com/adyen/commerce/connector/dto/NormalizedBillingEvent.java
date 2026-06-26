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

import java.time.Instant;
import java.util.Map;

import com.adyen.commerce.connector.enums.BillingPlatform;

/**
 * A verified, vendor-neutral billing event produced by a connector's {@code parseWebhook}. The core
 * reconciles SAP state from this; no vendor payload shape leaks past the connector boundary.
 */
public record NormalizedBillingEvent(BillingPlatform platform,
                                     BillingEventType type,
                                     String externalSubscriptionId,
                                     String externalCustomerId,
                                     Instant occurredAt,
                                     Map<String, String> attributes)
{
}
