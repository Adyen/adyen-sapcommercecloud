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

/**
 * Capabilities/constraints a connector advertises so the core can branch on capabilities instead of
 * hard-coding per-platform conditionals (design principle #4).
 *
 * @param requiresNetworkTransactionId the import requires the original NTID (Recurly = true)
 * @param supportsImmediateStart       a subscription can start immediately (Recurly import = false: future-dated only)
 * @param supportsPause                pause/resume is supported
 * @param requiresPreConfiguredPlan    a plan/price must already exist on the platform (all = true)
 * @param liveTokenValidationOnImport  the platform validates the token against Adyen at import (Chargebee = true)
 * @param tokenImportStyle             how the token pair is expressed on import
 */
public record ConnectorCapabilities(boolean requiresNetworkTransactionId,
                                    boolean supportsImmediateStart,
                                    boolean supportsPause,
                                    boolean requiresPreConfiguredPlan,
                                    boolean liveTokenValidationOnImport,
                                    TokenImportStyle tokenImportStyle)
{
}
