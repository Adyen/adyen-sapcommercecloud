package com.adyen.commerce.response;

import java.util.ArrayList;
import java.util.List;

public class ErrorResponse {
    private String errorCode;
    private List<String> invalidFields;

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

    public String getErrorCode() {
        return errorCode;
    }

    public List<String> getInvalidFields() {
        return invalidFields;
    }

}
