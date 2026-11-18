package com.adyen.commerce.occ.exceptionhandler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.Test;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.adyen.commerce.exception.AdyenControllerException;

import de.hybris.bootstrap.annotations.UnitTest;

/**
 * The Spartacus library reads {@code error.error.errorCode} / {@code invalidFields} from the place-order answer,
 * so the OCC handler answers 400 with exactly that JSON, serialized to a String.
 */
@UnitTest
public class AdyenOCCControllerExceptionHandlerTest
{
    private final AdyenOCCControllerExceptionHandler handler = new AdyenOCCControllerExceptionHandler();

    @Test
    public void shouldAnswer400WithErrorCodeAndInvalidFieldsAsJsonString()
    {
        final ResponseEntity<String> response = handler.handleAdyenControllerException(
                new AdyenControllerException("checkout.error.authorization.failed", Collections.singletonList("cvc")));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());
        assertTrue(response.getBody().contains("\"errorCode\":\"checkout.error.authorization.failed\""));
        assertTrue(response.getBody().contains("\"invalidFields\":[\"cvc\"]"));
    }

    @Test
    public void shouldPrecedeOccGenericExceptionAdvice()
    {
        final Order order = AdyenOCCControllerExceptionHandler.class.getAnnotation(Order.class);
        assertNotNull(order);
        assertEquals(Ordered.HIGHEST_PRECEDENCE, order.value());
    }
}
