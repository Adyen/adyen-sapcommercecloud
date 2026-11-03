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
package com.adyen.commerce.connector.chargebee.client;

import java.util.Map;

/**
 * Inputs for creating a Chargebee subscription (Product Catalog 2.0 subscription_for_items).
 *
 * @param customerId        the Chargebee customer id (path segment)
 * @param itemPriceId       the Chargebee item price id
 * @param quantity          subscribed quantity (min 1)
 * @param startEpochSeconds optional future start (epoch seconds); {@code null} = start now
 * @param subscriptionId    optional caller-supplied id for idempotency (SAP order code); {@code null} = Chargebee generates
 * @param metadata          optional free-form meta_data
 */
public record ChargebeeSubscriptionParams(String customerId,
                                          String itemPriceId,
                                          int quantity,
                                          Long startEpochSeconds,
                                          String subscriptionId,
                                          Map<String, String> metadata)
{
}
