package com.adyen.commerce.util;

import com.adyen.constants.ApiConstants;

public class ErrorMessageUtil {
    private static final String CHECKOUT_ERROR_AUTHORIZATION_PAYMENT_REFUSED = "checkout.error.authorization.payment.refused";
    private static final String CHECKOUT_ERROR_AUTHORIZATION_PAYMENT_NOT_FOUND = "checkout.error.authorization.payment.detail.not.found";
    private static final String CHECKOUT_ERROR_AUTHORIZATION_PAYMENT_RESTRICTED_CARD = "checkout.error.authorization.restricted.card";
    private static final String CHECKOUT_ERROR_AUTHORIZATION_PAYMENT_CVC_DECLINED = "checkout.error.authorization.cvc.declined";
    private static final String CHECKOUT_ERROR_AUTHORIZATION_PAYMENT_NOT_PERMITTED = "checkout.error.authorization.transaction.not.permitted";

    private ErrorMessageUtil(){}

    public static String getErrorMessageByRefusalReason(String refusalReason) {
        if (refusalReason != null) {
            switch (refusalReason) {
                case ApiConstants.RefusalReason.TRANSACTION_NOT_PERMITTED:
                    return CHECKOUT_ERROR_AUTHORIZATION_PAYMENT_NOT_PERMITTED;
                case ApiConstants.RefusalReason.CVC_DECLINED:
                    return CHECKOUT_ERROR_AUTHORIZATION_PAYMENT_CVC_DECLINED;
                case ApiConstants.RefusalReason.RESTRICTED_CARD:
                    return CHECKOUT_ERROR_AUTHORIZATION_PAYMENT_RESTRICTED_CARD;
                case ApiConstants.RefusalReason.PAYMENT_DETAIL_NOT_FOUND:
                    return CHECKOUT_ERROR_AUTHORIZATION_PAYMENT_NOT_FOUND;
                default:
                    return CHECKOUT_ERROR_AUTHORIZATION_PAYMENT_REFUSED;
            }
        }
        return CHECKOUT_ERROR_AUTHORIZATION_PAYMENT_REFUSED;
    }
}
