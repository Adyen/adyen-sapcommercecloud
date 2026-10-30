package com.adyen.commerce.response;

import com.adyen.v6.service.PaymentAuthorisationRefusedException;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

public class ErrorResponse {
    private String errorCode;
    private List<String> invalidFields;
    /** A storefront-relative path where the shopper can resolve the error, e.g. My Subscriptions; absent when there is none. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String linkPath;

    public ErrorResponse(String errorCode, List<String> invalidFields) {
        this.errorCode = errorCode;
        this.invalidFields = invalidFields;
    }

    public ErrorResponse() {
        this.errorCode = "checkout.error.default";
        this.invalidFields = new ArrayList<>();
    }

    public ErrorResponse(String errorCode) {
        this.errorCode = errorCode;
        this.invalidFields = new ArrayList<>();
    }

    /**
     * The answer for a payment a guard refused: {@code errorCode} is the message key the storefront resolves
     * (like every other code here), {@code linkPath} the path that helps the shopper, if any.
     */
    public static ErrorResponse of(PaymentAuthorisationRefusedException refusal) {
        ErrorResponse response = new ErrorResponse(refusal.getMessageKey());
        response.linkPath = refusal.getResolutionPath();
        return response;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public List<String> getInvalidFields() {
        return invalidFields;
    }

    public String getLinkPath() {
        return linkPath;
    }

}
