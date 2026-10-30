/*
 *  Adyen Hybris Extension
 *
 *  Copyright (c) 2026 Adyen B.V.
 *  This file is open source and available under the MIT license.
 *  See the LICENSE file for more info.
 */
package com.adyen.commerce.occ.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;

import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.adyen.commerce.exception.AdyenControllerException;
import com.adyen.commerce.facades.AdyenPartialPaymentOrderFacade;
import com.adyen.commerce.request.PartialPaymentOrderRequest;
import com.adyen.model.checkout.Amount;
import com.adyen.v6.service.PaymentAuthorisationRefusedException;

import de.hybris.bootstrap.annotations.UnitTest;

/**
 * A gift card order is refused before the card is touched: the controller hands the refusal to the controller advice
 * (which answers 400 with a JSON body carrying the code), instead of answering with the generic, code-less message
 * that every other failure gets.
 */
@UnitTest
public class AdyenPartialPaymentOrderControllerRefusalTest
{
    private final AdyenPartialPaymentOrderFacade facade = mock(AdyenPartialPaymentOrderFacade.class);
    private AdyenPartialPaymentOrderController controller;
    private PartialPaymentOrderRequest request;

    @Before
    public void setUp() throws Exception
    {
        controller = new AdyenPartialPaymentOrderController();
        final Field field = AdyenPartialPaymentOrderController.class.getDeclaredField("adyenPartialPaymentOrderFacade");
        field.setAccessible(true);
        field.set(controller, facade);
        request = new PartialPaymentOrderRequest();
        request.setAmount(new Amount().value(500L).currency("EUR"));
    }

    @Test
    public void aRefusalReachesTheAdviceWithItsOwnCode()
    {
        when(facade.createPartialPaymentOrder(any())).thenThrow(new PaymentAuthorisationRefusedException("subscriptionAlreadyOwned"));

        final AdyenControllerException thrown = assertThrows(AdyenControllerException.class,
                () -> controller.createPartialPaymentOrder(request));

        assertEquals("checkout.error.subscriptionAlreadyOwned", thrown.getErrorResponse().getErrorCode());
    }

    @Test
    public void anyOtherFailureStillGetsTheGenericAnswer()
    {
        when(facade.createPartialPaymentOrder(any())).thenThrow(new RuntimeException("boom"));

        final ResponseEntity<String> response = controller.createPartialPaymentOrder(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Unable to process partial payment order request", response.getBody());
    }
}
