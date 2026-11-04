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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import com.adyen.commerce.facades.AdyenCheckoutFacade;
import com.adyen.model.checkout.PaymentRequest;
import com.adyen.model.checkout.PaymentResponse;
import com.adyen.v6.service.AdyenShopperIpResolverService;
import com.adyen.v6.service.DefaultAdyenCheckoutApiService;
import com.adyen.v6.service.PaymentAuthorisationGuardService;
import com.adyen.v6.service.PaymentAuthorisationRefusedException;
import com.adyen.v6.service.PaymentAuthorisationStage;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commerceservices.order.CommerceCartService;
import de.hybris.platform.core.model.c2l.CurrencyModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.order.CartService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.user.UserService;
import jakarta.servlet.http.HttpServletRequest;

/**
 * PayPal's submit goes to Adyen through {@code sendPaymentRequest}, which has no cart to hand to a guard, so the
 * facade asks the payment guards itself, with the cart that is being paid, before that call: a refusal stops the
 * submit, reaches the controller as itself and nothing is sent to Adyen.
 */
@UnitTest
public class DefaultAdyenPayPalExpressCheckoutFacadeGuardTest
{
    private final PaymentAuthorisationGuardService guardService = mock(PaymentAuthorisationGuardService.class);
    private final DefaultAdyenCheckoutApiService apiService = mock(DefaultAdyenCheckoutApiService.class);
    private final CartService cartService = mock(CartService.class);
    private final CommerceCartService commerceCartService = mock(CommerceCartService.class);
    private final UserService userService = mock(UserService.class);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final CartModel cart = mock(CartModel.class);
    private final PaymentRequest paymentRequest = new PaymentRequest();
    private DefaultAdyenPayPalExpressCheckoutFacade facade;

    @Before
    public void setUp() throws Exception
    {
        facade = new DefaultAdyenPayPalExpressCheckoutFacade();
        facade.setModelService(mock(ModelService.class));
        facade.setPaymentAuthorisationGuardService(guardService);
        facade.setAdyenShopperIpResolverService(mock(AdyenShopperIpResolverService.class));
        facade.cartService = cartService;
        facade.commerceCartService = commerceCartService;
        facade.userService = userService;
        final AdyenCheckoutFacade checkoutFacade = mock(AdyenCheckoutFacade.class);
        facade.adyenCheckoutFacade = checkoutFacade;
        when(checkoutFacade.getAdyenPaymentService()).thenReturn(apiService);

        final CurrencyModel currency = mock(CurrencyModel.class);
        when(currency.getIsocode()).thenReturn("EUR");
        when(cart.getCurrency()).thenReturn(currency);
        when(cart.getTotalPrice()).thenReturn(10.0);
        when(cart.getCode()).thenReturn("CART-1");
        when(cartService.getSessionCart()).thenReturn(cart);
        when(userService.getCurrentUser()).thenReturn(mock(de.hybris.platform.core.model.user.UserModel.class));
        when(commerceCartService.getCartForCodeAndUser(any(), any())).thenReturn(cart);
        when(request.getRequestURL()).thenReturn(new StringBuffer("https://shop.local/paypal"));
        when(request.getRequestURI()).thenReturn("/paypal");
        when(request.getContextPath()).thenReturn("");
        when(request.getServerName()).thenReturn("shop.local");
        paymentRequest.setReference("CART-1");
    }

    @Test
    public void theCartSubmitAsksTheGuardsWithThePaymentStageBeforeSendingToAdyen() throws Exception
    {
        final PaymentResponse response = new PaymentResponse();
        when(apiService.sendPaymentRequest(any(), any())).thenReturn(response);

        assertSame(response, facade.onPayPalCartSubmit(request, paymentRequest));

        final InOrder order = inOrder(guardService, apiService);
        order.verify(guardService).check(cart, PaymentAuthorisationStage.PAYMENT);
        order.verify(apiService).sendPaymentRequest(any(), any());
    }

    @Test
    public void aRefusedCartSubmitNeverReachesAdyen() throws Exception
    {
        final PaymentAuthorisationRefusedException refusal = new PaymentAuthorisationRefusedException("subscriptionAlreadyOwned");
        doThrow(refusal).when(guardService).check(cart, PaymentAuthorisationStage.PAYMENT);

        final PaymentAuthorisationRefusedException thrown = assertThrows(PaymentAuthorisationRefusedException.class,
                () -> facade.onPayPalCartSubmit(request, paymentRequest));

        assertSame(refusal, thrown);
        verifyNoInteractions(apiService);
    }

    @Test
    public void aRefusedOccPdpSubmitNeverReachesAdyen() throws Exception
    {
        doThrow(new PaymentAuthorisationRefusedException("subscriptionLimitPerCart")).when(guardService)
                .check(cart, PaymentAuthorisationStage.PAYMENT);

        assertThrows(PaymentAuthorisationRefusedException.class, () -> facade.onPayPalPDPSubmitOCC(request, paymentRequest));

        verify(guardService).check(cart, PaymentAuthorisationStage.PAYMENT);
        verifyNoInteractions(apiService);
    }

    @Test
    public void withoutAGuardServiceTheSubmitStillGoesToAdyen() throws Exception
    {
        facade.setPaymentAuthorisationGuardService(null);
        when(apiService.sendPaymentRequest(any(), any())).thenReturn(new PaymentResponse());

        facade.onPayPalCartSubmit(request, paymentRequest);

        verify(apiService).sendPaymentRequest(any(), any());
    }
}
