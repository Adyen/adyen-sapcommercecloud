package com.adyen.commerce.controllers.exceptionhandlers;

import com.adyen.commerce.exception.AdyenControllerException;
import com.adyen.commerce.response.ErrorResponse;
import com.adyen.service.exception.ApiException;
import org.apache.log4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class AdyenControllerExceptionHandler {
    private static final Logger LOG = Logger.getLogger(AdyenControllerExceptionHandler.class);

    @ExceptionHandler(value = AdyenControllerException.class)
    public ResponseEntity<ErrorResponse> handleAdyenControllerException(AdyenControllerException exception) {
        return ResponseEntity.badRequest().body(exception.getErrorResponse());
    }

    @ExceptionHandler(value = ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException exception) {
        LOG.error("Api Exception: " +  exception.getResponseBody());

        return ResponseEntity.badRequest().build();
    }
}
