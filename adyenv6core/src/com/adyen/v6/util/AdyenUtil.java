package com.adyen.v6.util;

import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_ONECLICK;

public class AdyenUtil {
    public static boolean isOneClick(String adyenPaymentMethod) {
        return adyenPaymentMethod.indexOf(PAYMENT_METHOD_ONECLICK) == 0;
    }

}
