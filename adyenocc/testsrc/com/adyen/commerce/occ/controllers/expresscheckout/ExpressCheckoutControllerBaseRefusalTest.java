/*
 *  Adyen Hybris Extension
 *
 *  Copyright (c) 2026 Adyen B.V.
 *  This file is open source and available under the MIT license.
 *  See the LICENSE file for more info.
 */
package com.adyen.commerce.occ.controllers.expresscheckout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;

import com.adyen.commerce.constants.AdyenwebcommonsConstants;
import com.adyen.commerce.exception.AdyenControllerException;
import com.adyen.commerce.facades.AdyenExpressCheckoutFacade;
import com.adyen.model.checkout.PaymentRequest;
import com.adyen.v6.service.PaymentAuthorisationRefusedException;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commercefacades.order.CartFacade;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.commerceservices.strategies.CheckoutCustomerStrategy;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Collections;

/**
 * The OCC express checkouts (Apple Pay, Google Pay, PayPal cart/PDP) share this handler. A payment guard's refusal
 * has to reach the storefront with its own message key and link, not as the generic authorization failure; any other
 * failure still does.
 */
@UnitTest
public class ExpressCheckoutControllerBaseRefusalTest
{
    private final AdyenExpressCheckoutFacade facade = mock(AdyenExpressCheckoutFacade.class);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private ExpressCheckoutControllerBase controller;

    @Before
    public void setUp()
    {
        controller = new ExpressCheckoutControllerBase()
        {
            @Override
            public CartFacade getCartFacade()
            {
                return mock(CartFacade.class);
            }

            @Override
            public CheckoutCustomerStrategy getCheckoutCustomerStrategy()
            {
                return mock(CheckoutCustomerStrategy.class);
            }

            @Override
            public AdyenExpressCheckoutFacade getAdyenCheckoutApiFacade()
            {
                return facade;
            }
        };
    }

    @Test
    public void aRefusalOnTheCartExpressCheckoutKeepsItsMessageKeyAndLink() throws Exception
    {
        when(facade.expressCheckoutCartOCC(any(), any(), any(), any())).thenThrow(new PaymentAuthorisationRefusedException(
                "subscriptionAlreadyOwned", "checkout.error.subscriptionAlreadyOwned", Collections.emptyList(),
                "/my-account/subscriptions"));

        final AdyenControllerException thrown = assertThrows(AdyenControllerException.class,
                () -> controller.handlePayment(request, new PaymentRequest(), "googlepay", new AddressData(), null, false));

        assertEquals("checkout.error.subscriptionAlreadyOwned", thrown.getErrorResponse().getErrorCode());
        assertEquals("/my-account/subscriptions", thrown.getErrorResponse().getLinkPath());
    }

    @Test
    public void aRefusalOnThePdpExpressCheckoutKeepsItsMessageKey() throws Exception
    {
        when(facade.expressCheckoutPDPOCC(any(), any(), any(), any(), any()))
                .thenThrow(new PaymentAuthorisationRefusedException("subscriptionLimitPerCart"));

        final AdyenControllerException thrown = assertThrows(AdyenControllerException.class,
                () -> controller.handlePayment(request, new PaymentRequest(), "applepay", new AddressData(), "cart-1", true));

        assertEquals("checkout.error.subscriptionLimitPerCart", thrown.getErrorResponse().getErrorCode());
    }

    @Test
    public void anyOtherFailureStillArrivesAsTheGenericAuthorizationError() throws Exception
    {
        when(facade.expressCheckoutCartOCC(any(), any(), any(), any())).thenThrow(new IllegalStateException("broke"));

        final AdyenControllerException thrown = assertThrows(AdyenControllerException.class,
                () -> controller.handlePayment(request, new PaymentRequest(), "googlepay", new AddressData(), null, false));

        assertEquals(AdyenwebcommonsConstants.CHECKOUT_ERROR_AUTHORIZATION_FAILED, thrown.getErrorResponse().getErrorCode());
    }
}
