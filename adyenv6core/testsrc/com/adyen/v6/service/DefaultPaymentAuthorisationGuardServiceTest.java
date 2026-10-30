/*
 *  Adyen Hybris Extension
 *
 *  Copyright (c) 2026 Adyen B.V.
 *  This file is open source and available under the MIT license.
 *  See the LICENSE file for more info.
 */
package com.adyen.v6.service;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.adyen.v6.repository.CartRepository;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.order.CartService;

/** Which cart the guards judge, in which order, and that an empty list costs nothing. */
@UnitTest
public class DefaultPaymentAuthorisationGuardServiceTest
{
    private final CartService cartService = mock(CartService.class);
    private final CartRepository cartRepository = mock(CartRepository.class);
    private final PaymentAuthorisationGuard first = mock(PaymentAuthorisationGuard.class);
    private final PaymentAuthorisationGuard second = mock(PaymentAuthorisationGuard.class);
    private final CartModel sessionCart = mock(CartModel.class);
    private final CartData cartData = new CartData();
    private DefaultPaymentAuthorisationGuardService service;

    @Before
    public void setUp()
    {
        cartData.setCode("00001");
        service = new DefaultPaymentAuthorisationGuardService();
        service.setCartService(cartService);
        service.setCartRepository(cartRepository);
        service.setGuards(List.of(first, second));
        when(cartService.hasSessionCart()).thenReturn(true);
        when(cartService.getSessionCart()).thenReturn(sessionCart);
        when(sessionCart.getCode()).thenReturn("00001");
    }

    @Test
    public void everyGuardSeesTheSessionCartWhenItIsTheOneBeingPaid()
    {
        service.check(cartData, PaymentAuthorisationStage.PAYMENT);

        verify(first).beforeAuthorisation(sessionCart, PaymentAuthorisationStage.PAYMENT);
        verify(second).beforeAuthorisation(sessionCart, PaymentAuthorisationStage.PAYMENT);
        verifyNoInteractions(cartRepository);
    }

    @Test
    public void aCartThatIsNotTheSessionCartIsLookedUpByCode()
    {
        final CartModel other = mock(CartModel.class);
        when(sessionCart.getCode()).thenReturn("99999");
        when(cartRepository.getCart("00001")).thenReturn(other);

        service.check(cartData, PaymentAuthorisationStage.PARTIAL_PAYMENT_ORDER);

        verify(first).beforeAuthorisation(other, PaymentAuthorisationStage.PARTIAL_PAYMENT_ORDER);
    }

    @Test
    public void theFirstRefusalWinsAndLaterGuardsAreNotAsked()
    {
        final PaymentAuthorisationRefusedException refusal = new PaymentAuthorisationRefusedException("subscriptionAlreadyOwned");
        doThrow(refusal).when(first).beforeAuthorisation(any(), any());

        final PaymentAuthorisationRefusedException thrown = assertThrows(PaymentAuthorisationRefusedException.class,
                () -> service.check(cartData, PaymentAuthorisationStage.PAYMENT));

        assertSame(refusal, thrown);
        verify(second, never()).beforeAuthorisation(any(), any());
    }

    @Test
    public void withNoGuardsNeitherTheSessionNorTheDatabaseIsTouched()
    {
        service.setGuards(List.of());

        service.check(cartData, PaymentAuthorisationStage.PAYMENT);

        verifyNoInteractions(cartService, cartRepository);
    }

    @Test
    public void aCartThatCannotBeFoundDoesNotStopThePaymentAndNoGuardIsAsked()
    {
        when(sessionCart.getCode()).thenReturn("99999");
        when(cartRepository.getCart("00001")).thenReturn(null);

        service.check(cartData, PaymentAuthorisationStage.PAYMENT);

        verifyNoInteractions(first, second);
    }

    @Test
    public void aCartModelIsHandedToTheGuardsAsItIs()
    {
        final CartModel cart = mock(CartModel.class);

        service.check(cart, PaymentAuthorisationStage.PAYMENT);

        verify(first).beforeAuthorisation(cart, PaymentAuthorisationStage.PAYMENT);
        verifyNoInteractions(cartRepository);
    }
}
