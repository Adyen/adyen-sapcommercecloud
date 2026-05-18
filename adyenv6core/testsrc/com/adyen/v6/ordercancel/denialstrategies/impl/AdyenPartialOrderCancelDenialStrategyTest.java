package com.adyen.v6.ordercancel.denialstrategies.impl;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.security.PrincipalModel;
import de.hybris.platform.ordercancel.DefaultOrderCancelDenialReason;
import de.hybris.platform.ordercancel.OrderCancelDenialReason;
import de.hybris.platform.ordercancel.model.OrderCancelConfigModel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class AdyenPartialOrderCancelDenialStrategyTest {

    @Spy
    @InjectMocks
    private AdyenPartialOrderCancelDenialStrategy testObj;

    @Mock
    private OrderCancelConfigModel orderCancelConfigModelMock;
    @Mock
    private OrderModel orderModelMock;
    @Mock
    private PrincipalModel principalModelMock;

    @Test(expected = IllegalArgumentException.class)
    public void getCancelDenialReason_WhenOrderIsNull_ShouldThrowException() {
        testObj.getCancelDenialReason(orderCancelConfigModelMock, null, principalModelMock, false, false);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getCancelDenialReason_WhenOrderCancelConfigIsNull_ShouldThrowException() {
        testObj.getCancelDenialReason(null, orderModelMock, principalModelMock, false, false);
    }

    @Test
    public void getCancelDenialReason_WhenPartialFlagsFalse_ShouldReturnNull() {
        final OrderCancelDenialReason result = testObj.getCancelDenialReason(orderCancelConfigModelMock, orderModelMock, principalModelMock, false, false);

        assertNull(result);
    }

    @Test
    public void getCancelDenialReason_WhenPartialFlagsTrue_ShouldReturnTheDecision() {
        when(testObj.getReason()).thenReturn(new DefaultOrderCancelDenialReason());

        final OrderCancelDenialReason result = testObj.getCancelDenialReason(orderCancelConfigModelMock, orderModelMock, principalModelMock, true, true);

        assertNotNull(result);
    }
}
