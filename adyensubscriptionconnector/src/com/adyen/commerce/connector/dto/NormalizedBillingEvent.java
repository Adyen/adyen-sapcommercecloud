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
 *
 * <p>{@code eventId} is the platform's own id for the delivery and the dedup key: a redelivery carries
 * the same id. It is nullable, so a malformed payload degrades to "cannot dedup on the platform id"
 * instead of throwing out of {@code parseWebhook}, and the core falls back to a content-derived key.</p>
 *
 * <p>{@code occurredAt} is the platform-side timestamp and the ordering signal for the stale-event rule.
 * It is only as precise as the platform makes it — Chargebee's is whole seconds — so two events can
 * legitimately be indistinguishable in time.</p>
 */
public record NormalizedBillingEvent(BillingPlatform platform,
                                     BillingEventType type,
                                     String eventId,
                                     String externalSubscriptionId,
                                     String externalCustomerId,
                                     Instant occurredAt,
                                     Map<String, String> attributes)
{
	public NormalizedBillingEvent
	{
		Dtos.requireValue(platform, "platform");
		Dtos.requireValue(type, "type");
		attributes = Dtos.immutableCopy(attributes);
	}
}
