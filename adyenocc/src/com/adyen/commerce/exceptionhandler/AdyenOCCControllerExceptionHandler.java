package com.adyen.commerce.exceptionhandler;

import com.adyen.commerce.exception.AdyenControllerException;
import com.adyen.commerce.response.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class AdyenOCCControllerExceptionHandler {

    @ExceptionHandler(value = AdyenControllerException.class)
    public ResponseEntity<ErrorResponse> handleAdyenControllerException(AdyenControllerException exception) {
        return ResponseEntity.badRequest().body(exception.getErrorResponse());
    }
}