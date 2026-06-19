package com.adyen.v6.populator;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commercefacades.order.data.OrderData;
import de.hybris.platform.commercefacades.order.data.OrderEntryData;
import de.hybris.platform.commercefacades.product.data.ProductData;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.ordercancel.CancelDecision;
import de.hybris.platform.ordercancel.OrderCancelCancelableEntriesStrategy;
import de.hybris.platform.ordercancel.OrderCancelService;
import de.hybris.platform.servicelayer.user.UserService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class AdyenOrderCancelPopulatorTest {
    @Spy
    @InjectMocks
    private AdyenOrderCancelPopulator testObj;

    @Mock
    private OrderCancelService orderCancelServiceMock;
    @Mock
    private UserService userServiceMock;
    @Mock
    private OrderCancelCancelableEntriesStrategy orderCancelCancelableEntriesStrategy;

    @Mock
    private OrderModel orderModelMock;
    @Mock
    private UserModel userModelMock;
    @Mock
    private CancelDecision fullCancelDecisionMock;
    @Mock
    private CancelDecision partialCancelDecisionMock;

    private OrderData orderDataStub = new OrderData();
    private AbstractOrderEntryModel abstractOrderEntryModelStubOne, abstractOrderEntryModelStubTwo;
    private OrderEntryData orderEntryDataStubOne, orderEntryDataStubTwo;
    private ProductData productDataStubOne, productDataStubTwo;

    @Before
    public void setUp() throws Exception {
        abstractOrderEntryModelStubOne = new AbstractOrderEntryModel();
        abstractOrderEntryModelStubTwo = new AbstractOrderEntryModel();
        orderEntryDataStubOne = new OrderEntryData();
        orderEntryDataStubTwo = new OrderEntryData();
        productDataStubOne = new ProductData();
        productDataStubTwo = new ProductData();
        orderDataStub.setEntries(List.of(orderEntryDataStubOne, orderEntryDataStubTwo));
        orderEntryDataStubOne.setProduct(productDataStubOne);
        orderEntryDataStubTwo.setProduct(productDataStubTwo);
        orderEntryDataStubOne.setEntries(Collections.singletonList(orderEntryDataStubTwo));
        orderEntryDataStubTwo.setEntries(Collections.singletonList(orderEntryDataStubOne));
        orderEntryDataStubTwo.setEntryNumber(1);
        orderEntryDataStubOne.setEntryNumber(0);
        productDataStubOne.setMultidimensional(Boolean.TRUE);
        productDataStubTwo.setMultidimensional(Boolean.FALSE);
        when(userServiceMock.getCurrentUser()).thenReturn(userModelMock);
        when(orderCancelCancelableEntriesStrategy.getAllCancelableEntries(orderModelMock, userModelMock)).thenReturn(Map.of(abstractOrderEntryModelStubOne, 0L, abstractOrderEntryModelStubTwo, 1L));
    }

    @Test
    public void populate_shouldPopulateOrderCancellableFalse() {
        when(orderCancelServiceMock.isCancelPossible(orderModelMock, userModelMock, false, false)).thenReturn(partialCancelDecisionMock);
        when(partialCancelDecisionMock.isAllowed()).thenReturn(Boolean.FALSE);
        when(orderCancelServiceMock.isCancelPossible(orderModelMock, userModelMock, false, false)).thenReturn(fullCancelDecisionMock);
        when(fullCancelDecisionMock.isAllowed()).thenReturn(Boolean.FALSE);

        testObj.populate(orderModelMock, orderDataStub);

        assertThat(orderDataStub.isCancellable()).isFalse();
    }

    @Test(expected = IllegalArgumentException.class)
    public void populate_ShouldThrowException_whenOrderModelIsNull() {
        testObj.populate(null, orderDataStub);
    }

    @Test(expected = IllegalArgumentException.class)
    public void populate_ShouldThrowException_whenOrderDataIsNull() {
        testObj.populate(orderModelMock, null);
    }
}
