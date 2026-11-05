/*
 *  Adyen Hybris Extension
 *
 *  Copyright (c) 2026 Adyen B.V.
 *  This file is open source and available under the MIT license.
 *  See the LICENSE file for more info.
 */
package com.adyen.commerce.facades.impl;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;

import com.adyen.model.checkout.PaymentRequest;
import com.adyen.v6.service.AdyenCheckoutApiService;
import com.adyen.v6.service.AdyenShopperIpResolverService;
import com.adyen.v6.service.PaymentAuthorisationRefusedException;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commerceservices.strategies.CheckoutCustomerStrategy;
import jakarta.servlet.http.HttpServletRequest;

/**
 * The accelerator facade turns every unexpected failure of the payment request into an {@code IOException}; a payment
 * guard's refusal is not one, and has to reach the controller as itself or the shopper is told the request failed.
 */
@UnitTest
public class DefaultAdyenCheckoutFacadeRefusalTest
{
    private final AdyenCheckoutApiService apiService = mock(AdyenCheckoutApiService.class);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final CartData cartData = new CartData();
    private final PaymentAuthorisationRefusedException refusal = new PaymentAuthorisationRefusedException("subscriptionAlreadyOwned");
    private DefaultAdyenCheckoutFacade facade;

    @Before
    public void setUp() throws Exception
    {
        facade = spy(new DefaultAdyenCheckoutFacade());
        facade.setAdyenShopperIpResolverService(mock(AdyenShopperIpResolverService.class));
        doNothing().when(facade).updateCartWithSessionData(any());
        doReturn(apiService).when(facade).getAdyenPaymentService();
        doReturn("en").when(facade).getShopperLocale();
        doReturn(mock(CheckoutCustomerStrategy.class)).when(facade).getCheckoutCustomerStrategy();
        when(request.getRequestURL()).thenReturn(new StringBuffer("https://shop.local/checkout/place-order"));
        when(request.getRequestURI()).thenReturn("/checkout/place-order");
        when(apiService.processPaymentRequest(any(), any(), any(), any())).thenThrow(refusal);
    }

    @Test
    public void authorisePaymentLetsTheRefusalThroughUnwrapped() throws Exception
    {
        assertSame(refusal, assertThrows(PaymentAuthorisationRefusedException.class,
                () -> facade.authorisePayment(request, cartData)));
    }

    @Test
    public void componentPaymentLetsTheRefusalThroughUnwrapped() throws Exception
    {
        assertSame(refusal, assertThrows(PaymentAuthorisationRefusedException.class,
                () -> facade.componentPayment(request, cartData, new PaymentRequest())));
    }
}
