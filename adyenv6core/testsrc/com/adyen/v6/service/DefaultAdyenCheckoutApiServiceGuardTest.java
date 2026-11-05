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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.retry.support.RetryTemplate;

import com.adyen.commerce.services.AdyenRequestService;
import com.adyen.commerce.services.PaymentMethodNameOverrideService;
import com.adyen.model.checkout.PaymentRequest;
import com.adyen.v6.model.RequestInfo;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.store.BaseStoreModel;

/**
 * The payment guards speak before anything is sent to Adyen: in both {@code processPaymentRequest} overloads and
 * in the gift card leg, and a refusal leaves the request service untouched. Everything after the guard is stopped
 * by a sentinel thrown from the request service, so no HTTP is attempted.
 */
@UnitTest
public class DefaultAdyenCheckoutApiServiceGuardTest
{
    private AdyenRequestService requestService;
    private PaymentAuthorisationGuardService guardService;
    private DefaultAdyenCheckoutApiService service;
    private final CartData cartData = new CartData();
    private final RequestInfo requestInfo = mock(RequestInfo.class);
    private final CustomerModel customer = mock(CustomerModel.class);
    private final IllegalStateException sentinel = new IllegalStateException("past the guard");

    @Before
    public void setUp() throws Exception
    {
        requestService = mock(AdyenRequestService.class);
        guardService = mock(PaymentAuthorisationGuardService.class);
        final BaseStoreModel store = mock(BaseStoreModel.class);
        when(store.getAdyenTestMode()).thenReturn(Boolean.TRUE);
        service = new DefaultAdyenCheckoutApiService(store, "MERCHANT", requestService,
                mock(PaymentMethodNameOverrideService.class), new RetryTemplate(), new RetryTemplate());
        service.setPaymentAuthorisationGuardService(guardService);

        when(requestService.createPaymentsRequest(any(), any(), any(), any(), any(), any(), any(), any())).thenThrow(sentinel);
        when(requestService.createPartialPaymentRequest(any(), any(), any(), any(), any(), any(), any(), any(), any())).thenThrow(sentinel);
    }

    @Test
    public void thePaymentGuardsRunBeforeThePaymentsRequestIsBuilt() throws Exception
    {
        final IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> service.processPaymentRequest(cartData, new PaymentRequest(), requestInfo, customer, null));

        assertSame(sentinel, thrown);
        final InOrder order = inOrder(guardService, requestService);
        order.verify(guardService).check(cartData, PaymentAuthorisationStage.PAYMENT);
        order.verify(requestService).createPaymentsRequest(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    public void theShorterOverloadIsGuardedToo() throws Exception
    {
        assertThrows(IllegalStateException.class,
                () -> service.processPaymentRequest(cartData, new PaymentRequest(), requestInfo, customer));

        verify(guardService).check(cartData, PaymentAuthorisationStage.PAYMENT);
    }

    @Test
    public void aRefusalStopsBothOverloadsBeforeAnythingIsAssembledForAdyen() throws Exception
    {
        Mockito.doThrow(new PaymentAuthorisationRefusedException("subscriptionAlreadyOwned"))
                .when(guardService).check(any(CartData.class), any());

        assertThrows(PaymentAuthorisationRefusedException.class,
                () -> service.processPaymentRequest(cartData, new PaymentRequest(), requestInfo, customer));
        assertThrows(PaymentAuthorisationRefusedException.class,
                () -> service.processPaymentRequest(cartData, new PaymentRequest(), requestInfo, customer, null));

        verifyNoInteractions(requestService);
    }

    @Test
    public void theGiftCardLegIsGuardedAndARefusalStopsItBeforeTheRequestIsBuilt() throws Exception
    {
        Mockito.doThrow(new PaymentAuthorisationRefusedException("subscriptionLimitPerCart"))
                .when(guardService).check(cartData, PaymentAuthorisationStage.PARTIAL_PAYMENT);

        final PaymentAuthorisationRefusedException thrown = assertThrows(PaymentAuthorisationRefusedException.class,
                () -> service.processPartialPaymentRequest(cartData, new PaymentRequest(), requestInfo, customer,
                        BigDecimal.TEN, "EUR"));

        assertSame("subscriptionLimitPerCart", thrown.getErrorCode());
        verifyNoInteractions(requestService);
    }

    @Test
    public void withoutAGuardServiceNothingChanges() throws Exception
    {
        service.setPaymentAuthorisationGuardService(null);

        final IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> service.processPaymentRequest(cartData, new PaymentRequest(), requestInfo, customer, null));

        assertSame(sentinel, thrown);
        verify(requestService).createPaymentsRequest(eq("MERCHANT"), eq(cartData), any(), eq(requestInfo), eq(customer),
                any(), any(), any());
    }
}
