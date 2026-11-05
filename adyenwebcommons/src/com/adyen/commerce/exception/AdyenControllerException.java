package com.adyen.commerce.exception;

import com.adyen.commerce.response.ErrorResponse;
import com.adyen.v6.service.PaymentAuthorisationRefusedException;

import java.util.ArrayList;
import java.util.List;

public class AdyenControllerException extends RuntimeException {

    private final ErrorResponse errorResponse;

    public AdyenControllerException(String errorCode, List<String> invalidFields) {
        errorResponse = new ErrorResponse(errorCode, invalidFields);
    }

    public AdyenControllerException() {
        errorResponse = new ErrorResponse("checkout.error.default", new ArrayList<>());
    }

    public AdyenControllerException(String errorCode) {
        errorResponse = new ErrorResponse(errorCode);
    }

    public AdyenControllerException(PaymentAuthorisationRefusedException refusal) {
        errorResponse = ErrorResponse.of(refusal);
    }

    public ErrorResponse getErrorResponse() {
        return errorResponse;
    }
}
