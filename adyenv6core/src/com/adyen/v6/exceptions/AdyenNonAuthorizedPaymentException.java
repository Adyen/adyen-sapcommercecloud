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
 *  Copyright (c) 2017 Adyen B.V.
 *  This file is open source and available under the MIT license.
 *  See the LICENSE file for more info.
 */
package com.adyen.v6.exceptions;

import com.adyen.model.checkout.PaymentDetailsResponse;
import com.adyen.model.checkout.PaymentResponse;
import com.adyen.model.payment.PaymentResult;
import com.adyen.model.terminal.TerminalAPIResponse;

public class AdyenNonAuthorizedPaymentException extends Exception {
    private PaymentResult paymentResult;
    private PaymentResponse paymentsResponse;
    private TerminalAPIResponse terminalApiResponse;
    private PaymentDetailsResponse paymentsDetailsResponse;

    public AdyenNonAuthorizedPaymentException(PaymentResult paymentResult) {
        this.paymentResult = paymentResult;
    }

    public AdyenNonAuthorizedPaymentException(PaymentResponse paymentsResponse) {
        this.paymentsResponse = paymentsResponse;
    }

    public AdyenNonAuthorizedPaymentException(TerminalAPIResponse terminalApiResponse) {
        this.terminalApiResponse = terminalApiResponse;
    }

    public AdyenNonAuthorizedPaymentException(PaymentDetailsResponse paymentsDetailsResponse) {
        this.paymentsDetailsResponse = paymentsDetailsResponse;
    }

    public AdyenNonAuthorizedPaymentException(String message) {
        super(message);
    }

    public PaymentResult getPaymentResult() {
        return paymentResult;
    }

    public void setPaymentResult(PaymentResult paymentResult) {
        this.paymentResult = paymentResult;
    }

    public PaymentResponse getPaymentsResponse() {
        return paymentsResponse;
    }

    public void setPaymentResponse(PaymentResponse paymentsResponse) {
        this.paymentsResponse = paymentsResponse;
    }

    public TerminalAPIResponse getTerminalApiResponse() {
        return terminalApiResponse;
    }

    public void setTerminalApiResponse(TerminalAPIResponse terminalApiResponse) {
        this.terminalApiResponse = terminalApiResponse;
    }

    public PaymentDetailsResponse getPaymentsDetailsResponse() {
        return paymentsDetailsResponse;
    }

    public void setPaymentsDetailsResponse(PaymentDetailsResponse paymentsDetailsResponse) {
        this.paymentsDetailsResponse = paymentsDetailsResponse;
    }
}
