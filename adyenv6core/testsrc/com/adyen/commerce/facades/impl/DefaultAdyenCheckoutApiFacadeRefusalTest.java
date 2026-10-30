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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import org.junit.Before;
import org.junit.Test;

import com.adyen.commerce.data.AdyenPartialPaymentOrderData;
import com.adyen.commerce.facades.AdyenPartialPaymentOrderFacade;
import com.adyen.model.checkout.PaymentRequest;
import com.adyen.v6.enums.AdyenPartialPaymentStatus;
import com.adyen.v6.model.AdyenPartialPaymentOrderModel;
import com.adyen.v6.model.RequestInfo;
import com.adyen.v6.service.AdyenCheckoutApiService;
import com.adyen.v6.service.PaymentAuthorisationRefusedException;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commerceservices.strategies.CheckoutCustomerStrategy;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.order.CartService;

import java.util.List;

/**
 * A payment guard's refusal leaves {@code placeOrderWithPayment} as itself. When it comes on the remaining-amount
 * leg, after a gift card was charged, the gift card hold is released; on an ordinary payment there is nothing to
 * release.
 */
@UnitTest
public class DefaultAdyenCheckoutApiFacadeRefusalTest
{
    private final AdyenCheckoutApiService apiService = mock(AdyenCheckoutApiService.class);
    private final AdyenPartialPaymentOrderFacade partialPaymentOrderFacade = mock(AdyenPartialPaymentOrderFacade.class);
    private final CartService cartService = mock(CartService.class);
    private final CartModel cart = mock(CartModel.class);
    private final RequestInfo requestInfo = mock(RequestInfo.class);
    private final CartData cartData = new CartData();
    private final PaymentAuthorisationRefusedException refusal = new PaymentAuthorisationRefusedException("subscriptionAlreadyOwned");
    private DefaultAdyenCheckoutApiFacade facade;

    @Before
    public void setUp() throws Exception
    {
        facade = spy(new DefaultAdyenCheckoutApiFacade());
        facade.setCartService(cartService);
        facade.setAdyenPartialPaymentOrderFacade(partialPaymentOrderFacade);
        doReturn(apiService).when(facade).getAdyenPaymentService();
        doReturn("en").when(facade).getShopperLocale();
        doReturn(mock(CheckoutCustomerStrategy.class)).when(facade).getCheckoutCustomerStrategy();
        when(cartService.getSessionCart()).thenReturn(cart);
        final AdyenPartialPaymentOrderModel giftCard = mock(AdyenPartialPaymentOrderModel.class);
        when(giftCard.getPspReference()).thenReturn("GIFT-PSP");
        when(giftCard.getStatus()).thenReturn(AdyenPartialPaymentStatus.AUTHORIZED);
        when(cart.getAdyenPartialPaymentOrders()).thenReturn(List.of(giftCard));
        when(apiService.processPaymentRequest(any(), any(), any(), any(), any())).thenThrow(refusal);
    }

    @Test
    public void aRefusalOnTheRemainingAmountReleasesTheGiftCardHoldAndArrivesAsItself() throws Exception
    {
        final PaymentAuthorisationRefusedException thrown = assertThrows(PaymentAuthorisationRefusedException.class,
                () -> facade.placeOrderWithPayment(null, cartData, new PaymentRequest(), requestInfo,
                        new AdyenPartialPaymentOrderData()));

        assertSame(refusal, thrown);
        verify(partialPaymentOrderFacade).cancelPartialPaymentOrder("GIFT-PSP");
    }

    @Test
    public void aRefusalOnAnOrdinaryPaymentHasNoGiftCardToRelease() throws Exception
    {
        final PaymentAuthorisationRefusedException thrown = assertThrows(PaymentAuthorisationRefusedException.class,
                () -> facade.placeOrderWithPayment(null, cartData, new PaymentRequest(), requestInfo));

        assertSame(refusal, thrown);
        verify(partialPaymentOrderFacade, never()).cancelPartialPaymentOrder(any());
    }
}
