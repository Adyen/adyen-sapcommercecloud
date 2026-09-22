package com.adyen.commerce.occ.exceptionhandler;

import com.adyen.commerce.exception.AdyenControllerException;
import com.adyen.commerce.response.ErrorResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.log4j.Logger;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class AdyenOCCControllerExceptionHandler {

    private static final Logger LOG = Logger.getLogger(AdyenOCCControllerExceptionHandler.class);

    private static final String FALLBACK_ERROR_BODY = "{\"errorCode\":\"checkout.error.default\",\"invalidFields\":[]}";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Serialized by hand for the same reason as the accelerator addon handler: an exception handler is
     * rendered by ExceptionHandlerExceptionResolver, which carries its own message converter list rather
     * than the one configured for regular request handling. Returning a POJO can therefore fail with
     * "No acceptable representation" and hide the real error from the storefront, so a String body is used.
     */
    @ExceptionHandler(value = AdyenControllerException.class)
    public ResponseEntity<String> handleAdyenControllerException(AdyenControllerException exception) {
        String body;
        try {
            body = OBJECT_MAPPER.writeValueAsString(exception.getErrorResponse());
        } catch (JsonProcessingException e) {
            LOG.error("Unable to serialize Adyen error response", e);
            body = FALLBACK_ERROR_BODY;
        }

        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
