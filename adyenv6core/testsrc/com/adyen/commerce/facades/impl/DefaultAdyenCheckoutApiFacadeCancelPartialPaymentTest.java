package com.adyen.commerce.facades.impl;

import com.adyen.commerce.facades.AdyenPartialPaymentOrderFacade;
import com.adyen.v6.enums.AdyenPartialPaymentStatus;
import com.adyen.v6.model.AdyenPartialPaymentOrderModel;
import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.order.CartService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Mockito.*;

@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class DefaultAdyenCheckoutApiFacadeCancelPartialPaymentTest {

    private static final String PSP_REFERENCE_1 = "GCB_ORDER_001";
    private static final String PSP_REFERENCE_2 = "GCB_ORDER_002";

    @Spy
    @InjectMocks
    private DefaultAdyenCheckoutApiFacade testObj;

    @Mock
    private CartService cartServiceMock;

    @Mock
    private AdyenPartialPaymentOrderFacade adyenPartialPaymentOrderFacadeMock;

    @Mock
    private CartModel cartModelMock;

    @Mock
    private AdyenPartialPaymentOrderModel partialPaymentCreatedMock;

    @Mock
    private AdyenPartialPaymentOrderModel partialPaymentAuthorizedMock;

    @Mock
    private AdyenPartialPaymentOrderModel partialPaymentCapturedMock;

    @Before
    public void setUp() {
        when(cartServiceMock.getSessionCart()).thenReturn(cartModelMock);

        when(partialPaymentCreatedMock.getPspReference()).thenReturn(PSP_REFERENCE_1);
        when(partialPaymentCreatedMock.getStatus()).thenReturn(AdyenPartialPaymentStatus.CREATED);

        when(partialPaymentAuthorizedMock.getPspReference()).thenReturn(PSP_REFERENCE_2);
        when(partialPaymentAuthorizedMock.getStatus()).thenReturn(AdyenPartialPaymentStatus.AUTHORIZED);

        when(partialPaymentCapturedMock.getPspReference()).thenReturn(PSP_REFERENCE_2);
        when(partialPaymentCapturedMock.getStatus()).thenReturn(AdyenPartialPaymentStatus.CAPTURED);
    }

    @Test
    public void cancelPartialPaymentOrdersOnFailure_shouldCancelCreatedOrders() {
        when(cartModelMock.getAdyenPartialPaymentOrders()).thenReturn(
                Arrays.asList(partialPaymentCreatedMock, partialPaymentAuthorizedMock, partialPaymentCapturedMock));

        testObj.cancelPartialPaymentOrdersOnFailure();

        verify(adyenPartialPaymentOrderFacadeMock).cancelPartialPaymentOrder(PSP_REFERENCE_1);
        verify(adyenPartialPaymentOrderFacadeMock, never()).cancelPartialPaymentOrder(PSP_REFERENCE_2);
    }

    @Test
    public void cancelPartialPaymentOrdersOnFailure_shouldSkipWhenNoCart() {
        when(cartServiceMock.getSessionCart()).thenReturn(null);
        testObj.cancelPartialPaymentOrdersOnFailure();
        verify(adyenPartialPaymentOrderFacadeMock, never()).cancelPartialPaymentOrder(any());
    }

    @Test
    public void cancelPartialPaymentOrdersOnFailure_shouldSkipWhenNoPartialPayments() {
        when(cartModelMock.getAdyenPartialPaymentOrders()).thenReturn(null);
        testObj.cancelPartialPaymentOrdersOnFailure();
        verify(adyenPartialPaymentOrderFacadeMock, never()).cancelPartialPaymentOrder(any());
    }

    @Test
    public void cancelPartialPaymentOrdersOnFailure_shouldSkipWhenEmptyList() {
        when(cartModelMock.getAdyenPartialPaymentOrders()).thenReturn(Collections.emptyList());
        testObj.cancelPartialPaymentOrdersOnFailure();
        verify(adyenPartialPaymentOrderFacadeMock, never()).cancelPartialPaymentOrder(any());
    }

    @Test
    public void cancelPartialPaymentOrdersOnFailure_shouldContinueOnException() {
        when(cartModelMock.getAdyenPartialPaymentOrders()).thenReturn(
                Arrays.asList(partialPaymentCreatedMock, partialPaymentAuthorizedMock));
        doThrow(new RuntimeException("API error")).when(adyenPartialPaymentOrderFacadeMock)
                .cancelPartialPaymentOrder(PSP_REFERENCE_1);

        testObj.cancelPartialPaymentOrdersOnFailure();

        verify(adyenPartialPaymentOrderFacadeMock).cancelPartialPaymentOrder(PSP_REFERENCE_1);
        verify(adyenPartialPaymentOrderFacadeMock).cancelPartialPaymentOrder(PSP_REFERENCE_2);
    }
}
