package com.adyen.commerce.api.controllers.exceptionhandlers;

import com.adyen.commerce.exception.AdyenControllerException;
import com.adyen.commerce.response.ErrorResponse;
import com.adyen.service.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.log4j.Logger;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@ControllerAdvice
public class AdyenControllerExceptionHandler {
    private static final Logger LOG = Logger.getLogger(AdyenControllerExceptionHandler.class);


    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @ExceptionHandler(AdyenControllerException.class)
    public void handleAdyenControllerException(
            AdyenControllerException exception,
            HttpServletResponse response) throws IOException {

        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        OBJECT_MAPPER.writeValue(
                response.getOutputStream(),
                exception.getErrorResponse()
        );
    }

    @ExceptionHandler(value = ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException exception) {
        LOG.error("Api Exception: " +  exception.getResponseBody());

        return ResponseEntity.badRequest().build();
    }
}
