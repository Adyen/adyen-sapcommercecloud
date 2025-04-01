package com.adyen.v6.service;

import com.adyen.model.checkout.PaymentCaptureResponse;
import com.adyen.model.checkout.PaymentRefundResponse;
import com.adyen.model.checkout.PaymentReversalResponse;

import java.math.BigDecimal;
import java.util.Currency;

public interface AdyenModificationsApiService {
    /**
     * Performs Capture request via Adyen API
     */
    PaymentCaptureResponse capture(BigDecimal amount, Currency currency, String authReference, String merchantReference) throws Exception;

    PaymentReversalResponse cancelOrRefund(final String paymentPspReference, final String merchantReference) throws Exception;

    PaymentRefundResponse refund(final BigDecimal amount, final Currency currency, final String paymentPspReference, final String reference) throws Exception;


}
