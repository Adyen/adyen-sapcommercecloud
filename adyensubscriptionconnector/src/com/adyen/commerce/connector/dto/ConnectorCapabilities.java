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
 * Capabilities and constraints a connector advertises, so the core can branch on capabilities instead of
 * hard-coding per-platform conditionals.
 *
 * @param requiresNetworkTransactionId the import requires the original NTID (Recurly = true)
 * @param supportsImmediateStart       a subscription can start immediately (Recurly import = false: future-dated only)
 * @param supportsPause                pause/resume is supported
 * @param requiresPreConfiguredPlan    a plan/price must already exist on the platform (all = true)
 * @param liveTokenValidationOnImport  the platform validates the token against Adyen at import (Chargebee = true)
 * @param tokenImportStyle             how the token pair is expressed on import
 * @param paymentMethodChange          whose billing a shopper-initiated payment-method change moves and
 *                                     which sources it accepts, or {@code NONE} where it cannot be done
 * @param paymentMethodEnrollment      whether the platform hosts a page where the shopper can give it a
 *                                     payment method, and what arriving there does, or {@code NONE}
 */
public record ConnectorCapabilities(boolean requiresNetworkTransactionId,
                                    boolean supportsImmediateStart,
                                    boolean supportsPause,
                                    boolean requiresPreConfiguredPlan,
                                    boolean liveTokenValidationOnImport,
                                    TokenImportStyle tokenImportStyle,
                                    PaymentMethodChangeSupport paymentMethodChange,
                                    PaymentMethodEnrollmentSupport paymentMethodEnrollment)
{
	public ConnectorCapabilities
	{
		Dtos.requireValue(tokenImportStyle, "tokenImportStyle");
		// No default: whether a shopper can change their card is a question each adapter has to answer,
		// and a wrongly inherited answer is a control the platform cannot honour.
		Dtos.requireValue(paymentMethodChange, "paymentMethodChange");
		Dtos.requireValue(paymentMethodEnrollment, "paymentMethodEnrollment");
	}
}
