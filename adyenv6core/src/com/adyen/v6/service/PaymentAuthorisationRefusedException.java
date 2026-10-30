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
package com.adyen.v6.service;

import java.util.List;

/**
 * A {@link PaymentAuthorisationGuard} refused the payment. Nothing has been sent to Adyen.
 *
 * <p>Unchecked, like {@code RecurringContractHelper.TokenizationNotSupportedException}: the checkout facades'
 * signatures are fixed and every layer between the guard and the controller would otherwise have to declare it.
 * Carries no personal data, so it can go into a log line and a response body.</p>
 */
public class PaymentAuthorisationRefusedException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    /** Prefix of the message key the storefronts resolve: {@code checkout.error.<errorCode>}. */
    public static final String MESSAGE_KEY_PREFIX = "checkout.error.";

    private final String errorCode;
    private final String messageKey;
    private final List<String> messageArgs;
    private final String resolutionPath;

    public PaymentAuthorisationRefusedException(final String errorCode)
    {
        this(errorCode, MESSAGE_KEY_PREFIX + errorCode, List.of(), null);
    }

    /**
     * @param errorCode      a short code naming the reason, for logs and clients
     * @param messageKey     the localization key of the message to show the shopper
     * @param messageArgs    arguments of that message, possibly empty
     * @param resolutionPath a storefront path where the shopper can resolve the refusal, or {@code null}
     */
    public PaymentAuthorisationRefusedException(final String errorCode, final String messageKey,
            final List<String> messageArgs, final String resolutionPath)
    {
        super("Payment not authorised: " + errorCode);
        this.errorCode = errorCode;
        this.messageKey = messageKey;
        this.messageArgs = messageArgs == null ? List.of() : List.copyOf(messageArgs);
        this.resolutionPath = resolutionPath;
    }

    public String getErrorCode()
    {
        return errorCode;
    }

    public String getMessageKey()
    {
        return messageKey;
    }

    public List<String> getMessageArgs()
    {
        return messageArgs;
    }

    /** @return a storefront-relative path that helps the shopper out of the refusal, or {@code null} */
    public String getResolutionPath()
    {
        return resolutionPath;
    }
}
