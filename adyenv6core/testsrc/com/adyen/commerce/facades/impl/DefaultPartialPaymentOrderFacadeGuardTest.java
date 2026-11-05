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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;

import com.adyen.commerce.request.PartialPaymentOrderRequest;
import com.adyen.model.checkout.Amount;
import com.adyen.v6.factory.AdyenPaymentServiceFactory;
import com.adyen.v6.service.PaymentAuthorisationGuardService;
import com.adyen.v6.service.PaymentAuthorisationRefusedException;
import com.adyen.v6.service.PaymentAuthorisationStage;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commercefacades.order.CartFacade;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.store.services.BaseStoreService;

/**
 * A cart that the payment guards refuse must not get a gift card order: nothing is sent to Adyen, and the refusal
 * reaches the controller as itself instead of being flattened into the facade's generic internal error.
 */
@UnitTest
public class DefaultPartialPaymentOrderFacadeGuardTest
{
    private final CartFacade cartFacade = mock(CartFacade.class);
    private final PaymentAuthorisationGuardService guardService = mock(PaymentAuthorisationGuardService.class);
    private final AdyenPaymentServiceFactory serviceFactory = mock(AdyenPaymentServiceFactory.class);
    private final BaseStoreService baseStoreService = mock(BaseStoreService.class);
    private final CartData cartData = new CartData();
    private DefaultPartialPaymentOrderFacade facade;
    private PartialPaymentOrderRequest request;

    @Before
    public void setUp()
    {
        facade = new DefaultPartialPaymentOrderFacade();
        facade.setCartFacade(cartFacade);
        facade.setPaymentAuthorisationGuardService(guardService);
        facade.setAdyenPaymentServiceFactory(serviceFactory);
        facade.setBaseStoreService(baseStoreService);
        when(cartFacade.getSessionCart()).thenReturn(cartData);

        request = new PartialPaymentOrderRequest();
        request.setAmount(new Amount().value(500L).currency("EUR"));
    }

    @Test
    public void aRefusedCartGetsNoPartialPaymentOrderAndTheRefusalArrivesUnchanged()
    {
        final PaymentAuthorisationRefusedException refusal = new PaymentAuthorisationRefusedException("subscriptionAlreadyOwned");
        doThrow(refusal).when(guardService).check(any(CartData.class), any());

        final PaymentAuthorisationRefusedException thrown = assertThrows(PaymentAuthorisationRefusedException.class,
                () -> facade.createPartialPaymentOrder(request));

        assertSame(refusal, thrown);
        verify(guardService).check(cartData, PaymentAuthorisationStage.PARTIAL_PAYMENT_ORDER);
        verifyNoInteractions(serviceFactory, baseStoreService);
    }
}
