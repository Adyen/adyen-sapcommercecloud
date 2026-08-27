package com.adyen.commerce.api.controllers.exceptionhandlers;

import com.adyen.commerce.exception.AdyenControllerException;
import com.adyen.commerce.response.ErrorResponse;
import com.adyen.service.exception.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private static final String FALLBACK_ERROR_BODY = "{\"errorCode\":\"checkout.error.default\",\"invalidFields\":[]}";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @ExceptionHandler(value = AdyenControllerException.class)
    public ResponseEntity<String> handleAdyenControllerException(AdyenControllerException exception) {
        return badRequestWithJsonBody(exception.getErrorResponse());
    }

    @ExceptionHandler(value = ApiException.class)
    public ResponseEntity<String> handleApiException(ApiException exception) {
        LOG.error("Api Exception: " +  exception.getResponseBody());

        return badRequestWithJsonBody(new ErrorResponse());
    }

    /**
     * Serializes the error body by hand rather than returning a ResponseEntity&lt;ErrorResponse&gt;.
     *
     * ExceptionHandlerExceptionResolver keeps its own message converter list, separate from the one
     * configured on RequestMappingHandlerAdapter, and the storefront registers Jackson only on the
     * latter (see yb2cacceleratorstorefront spring-mvc-config.xml). Returning a POJO from here therefore
     * failed with "HttpMediaTypeNotAcceptableException: No acceptable representation", which swallowed the
     * actual error (for example a refused card) and left the frontend without an errorCode. A String body
     * is writable by StringHttpMessageConverter, which is always present.
     */
    protected ResponseEntity<String> badRequestWithJsonBody(ErrorResponse errorResponse) {
        String body;
        try {
            body = OBJECT_MAPPER.writeValueAsString(errorResponse);
        } catch (JsonProcessingException e) {
            LOG.error("Unable to serialize Adyen error response", e);
            body = FALLBACK_ERROR_BODY;
        }

        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
