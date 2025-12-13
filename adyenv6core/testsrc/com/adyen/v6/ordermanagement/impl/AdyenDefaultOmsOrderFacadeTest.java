package com.adyen.v6.ordermanagement.impl;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.ordermanagementfacades.cancellation.data.OrderCancelEntryData;
import de.hybris.platform.ordermanagementfacades.cancellation.data.OrderCancelRequestData;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class AdyenDefaultOmsOrderFacadeTest {
    @InjectMocks
    private AdyenDefaultOmsOrderFacade testObj;

    @Mock
    private OrderCancelRequestData orderCancelRequestDataMock;
    @Mock
    private OrderModel orderModelMock;
    @Mock
    private OrderCancelEntryData orderCancelEntryDataMock;
    @Mock
    private AbstractOrderEntryModel entryModelMock1, entryModelMock2;

    @Test
    public void isPartialCancel_WhenEntriesMatch_ShouldReturnFalse() {
        when(orderCancelRequestDataMock.getEntries()).thenReturn(singletonList(orderCancelEntryDataMock));
        when(orderCancelEntryDataMock.getOrderEntryNumber()).thenReturn(1);
        when(orderModelMock.getEntries()).thenReturn(singletonList(entryModelMock1));
        when(entryModelMock1.getEntryNumber()).thenReturn(1);

        assertFalse(testObj.isPartialCancel(orderCancelRequestDataMock, orderModelMock));
    }

    @Test
    public void isPartialCancel_WhenEntriesDoNotMatch_ShouldReturnTrue() {
        when(orderCancelRequestDataMock.getEntries()).thenReturn(singletonList(orderCancelEntryDataMock));
        when(orderCancelEntryDataMock.getOrderEntryNumber()).thenReturn(1);
        when(orderModelMock.getEntries()).thenReturn(asList(entryModelMock1, entryModelMock2));
        when(entryModelMock1.getEntryNumber()).thenReturn(1);
        when(entryModelMock2.getEntryNumber()).thenReturn(2);

        assertTrue(testObj.isPartialCancel(orderCancelRequestDataMock, orderModelMock));
    }
}
