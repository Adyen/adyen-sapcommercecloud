package com.adyen.commerce.facades.impl;

import com.adyen.model.checkout.CancelOrderRequest;
import com.adyen.model.checkout.CancelOrderResponse;
import com.adyen.service.checkout.OrdersApi;
import com.adyen.service.exception.ApiException;
import com.adyen.v6.enums.AdyenPartialPaymentStatus;
import com.adyen.v6.factory.AdyenPaymentServiceFactory;
import com.adyen.v6.model.AdyenPartialPaymentOrderModel;
import com.adyen.v6.repository.AdyenPartialPaymentOrderRepository;
import com.adyen.v6.service.DefaultAdyenCheckoutApiService;
import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class DefaultPartialPaymentOrderFacadeTest {

    private static final String PSP_REFERENCE = "GCB1234567890";
    private static final String MERCHANT_ACCOUNT = "TestMerchant";

    @Spy
    @InjectMocks
    private DefaultPartialPaymentOrderFacade testObj;

    @Mock
    private BaseStoreService baseStoreServiceMock;

    @Mock
    private AdyenPaymentServiceFactory adyenPaymentServiceFactoryMock;

    @Mock
    private ModelService modelServiceMock;

    @Mock
    private AdyenPartialPaymentOrderRepository adyenPartialPaymentOrderRepositoryMock;

    @Mock
    private BaseStoreModel baseStoreModelMock;

    @Mock
    private DefaultAdyenCheckoutApiService adyenCheckoutApiServiceMock;

    @Mock
    private OrdersApi ordersApiMock;

    @Mock
    private AdyenPartialPaymentOrderModel partialPaymentOrderModelMock;

    @Mock
    private CancelOrderResponse cancelOrderResponseMock;

    @Captor
    private ArgumentCaptor<CancelOrderRequest> cancelOrderRequestCaptor;

    @Before
    public void setUp() {
        doReturn(ordersApiMock).when(testObj).createOrdersApi(adyenCheckoutApiServiceMock);
        when(baseStoreServiceMock.getCurrentBaseStore()).thenReturn(baseStoreModelMock);
        when(baseStoreModelMock.getAdyenMerchantAccount()).thenReturn(MERCHANT_ACCOUNT);
        when(adyenPaymentServiceFactoryMock.createAdyenCheckoutApiService(baseStoreModelMock))
                .thenReturn(adyenCheckoutApiServiceMock);
    }

    @Test
    public void cancelPartialPaymentOrder_shouldCallAdyenApiAndUpdateStatus() throws Exception {
        when(adyenPartialPaymentOrderRepositoryMock.findPartialPaymentOrderByPspReference(PSP_REFERENCE))
                .thenReturn(partialPaymentOrderModelMock);
        when(ordersApiMock.cancelOrder(any(CancelOrderRequest.class))).thenReturn(cancelOrderResponseMock);

        testObj.cancelPartialPaymentOrder(PSP_REFERENCE);

        verify(ordersApiMock).cancelOrder(cancelOrderRequestCaptor.capture());
        CancelOrderRequest captured = cancelOrderRequestCaptor.getValue();
        assertEquals(MERCHANT_ACCOUNT, captured.getMerchantAccount());
        verify(partialPaymentOrderModelMock).setStatus(AdyenPartialPaymentStatus.CANCELLED);
        verify(modelServiceMock).save(partialPaymentOrderModelMock);
    }

    @Test
    public void cancelPartialPaymentOrder_shouldSendLatestStoredOrderData() throws Exception {
        when(adyenPartialPaymentOrderRepositoryMock.findPartialPaymentOrderByPspReference(PSP_REFERENCE))
                .thenReturn(partialPaymentOrderModelMock);
        when(partialPaymentOrderModelMock.getOrderData()).thenReturn("LATEST_ORDER_DATA");
        when(ordersApiMock.cancelOrder(any(CancelOrderRequest.class))).thenReturn(cancelOrderResponseMock);

        testObj.cancelPartialPaymentOrder(PSP_REFERENCE);

        verify(ordersApiMock).cancelOrder(cancelOrderRequestCaptor.capture());
        assertEquals("LATEST_ORDER_DATA", cancelOrderRequestCaptor.getValue().getOrder().getOrderData());
        assertEquals(PSP_REFERENCE, cancelOrderRequestCaptor.getValue().getOrder().getPspReference());
    }

    @Test
    public void cancelPartialPaymentOrder_withOrderPair_shouldSendOrderPspAndGivenOrderData() throws Exception {
        when(adyenPartialPaymentOrderRepositoryMock.findPartialPaymentOrderByPspReference(PSP_REFERENCE))
                .thenReturn(partialPaymentOrderModelMock);
        when(ordersApiMock.cancelOrder(any(CancelOrderRequest.class))).thenReturn(cancelOrderResponseMock);

        testObj.cancelPartialPaymentOrder(PSP_REFERENCE, "ORDER_PSP", "GIVEN_ORDER_DATA");

        verify(ordersApiMock).cancelOrder(cancelOrderRequestCaptor.capture());
        assertEquals("ORDER_PSP", cancelOrderRequestCaptor.getValue().getOrder().getPspReference());
        assertEquals("GIVEN_ORDER_DATA", cancelOrderRequestCaptor.getValue().getOrder().getOrderData());
        verify(adyenPartialPaymentOrderRepositoryMock).findPartialPaymentOrderByPspReference(PSP_REFERENCE);
        verify(partialPaymentOrderModelMock).setStatus(AdyenPartialPaymentStatus.CANCELLED);
        verify(modelServiceMock).save(partialPaymentOrderModelMock);
    }

    @Test
    public void cancelPartialPaymentOrder_withOrderPair_shouldFallBackToStoredOrderDataWhenGivenIsEmpty() throws Exception {
        when(adyenPartialPaymentOrderRepositoryMock.findPartialPaymentOrderByPspReference(PSP_REFERENCE))
                .thenReturn(partialPaymentOrderModelMock);
        when(partialPaymentOrderModelMock.getOrderData()).thenReturn("STORED_DATA");
        when(ordersApiMock.cancelOrder(any(CancelOrderRequest.class))).thenReturn(cancelOrderResponseMock);

        testObj.cancelPartialPaymentOrder(PSP_REFERENCE, "ORDER_PSP", "");

        verify(ordersApiMock).cancelOrder(cancelOrderRequestCaptor.capture());
        assertEquals("STORED_DATA", cancelOrderRequestCaptor.getValue().getOrder().getOrderData());
    }

    @Test
    public void cancelPartialPaymentOrder_withOrderPair_shouldNotThrowWhenSavingTheStatusFailsAfterAdyenCancelled() throws Exception {
        when(adyenPartialPaymentOrderRepositoryMock.findPartialPaymentOrderByPspReference(PSP_REFERENCE))
                .thenReturn(partialPaymentOrderModelMock);
        when(ordersApiMock.cancelOrder(any(CancelOrderRequest.class))).thenReturn(cancelOrderResponseMock);
        doThrow(new RuntimeException("db down")).when(modelServiceMock).save(partialPaymentOrderModelMock);

        testObj.cancelPartialPaymentOrder(PSP_REFERENCE, "ORDER_PSP", "DATA");

        verify(ordersApiMock).cancelOrder(any(CancelOrderRequest.class));
    }

    @Test
    public void cancelPartialPaymentOrder_withOrderPair_shouldLeaveStatusUntouchedWhenAdyenRejects() throws Exception {
        when(adyenPartialPaymentOrderRepositoryMock.findPartialPaymentOrderByPspReference(PSP_REFERENCE))
                .thenReturn(partialPaymentOrderModelMock);
        when(ordersApiMock.cancelOrder(any(CancelOrderRequest.class)))
                .thenThrow(new ApiException("The provided Order Data is not valid. ErrorCode: 14_037", 422));

        try {
            testObj.cancelPartialPaymentOrder(PSP_REFERENCE, "ORDER_PSP", "DATA");
            org.junit.Assert.fail("expected RuntimeException");
        } catch (RuntimeException e) {
            // expected
        }

        verify(partialPaymentOrderModelMock, never()).setStatus(any());
        verify(modelServiceMock, never()).save(any());
    }

    @Test(expected = IllegalArgumentException.class)
    public void cancelPartialPaymentOrder_withOrderPair_shouldThrowWhenOrderPspIsEmpty() {
        testObj.cancelPartialPaymentOrder(PSP_REFERENCE, "", "DATA");
    }

    @Test
    public void cancelPartialPaymentOrder_shouldLeaveStatusUntouchedWhenAdyenRejects() throws Exception {
        when(adyenPartialPaymentOrderRepositoryMock.findPartialPaymentOrderByPspReference(PSP_REFERENCE))
                .thenReturn(partialPaymentOrderModelMock);
        when(ordersApiMock.cancelOrder(any(CancelOrderRequest.class)))
                .thenThrow(new ApiException("The provided Order Data is not valid. ErrorCode: 14_037", 422));

        try {
            testObj.cancelPartialPaymentOrder(PSP_REFERENCE);
            org.junit.Assert.fail("expected RuntimeException");
        } catch (RuntimeException e) {
            // expected
        }

        verify(partialPaymentOrderModelMock, never()).setStatus(any());
        verify(modelServiceMock, never()).save(any());
    }

    @Test(expected = IllegalArgumentException.class)
    public void cancelPartialPaymentOrder_shouldThrowWhenPspReferenceIsNull() {
        testObj.cancelPartialPaymentOrder(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void cancelPartialPaymentOrder_shouldThrowWhenPspReferenceIsEmpty() {
        testObj.cancelPartialPaymentOrder("");
    }

    @Test
    public void cancelPartialPaymentOrder_shouldSkipWhenOrderNotFound() {
        when(adyenPartialPaymentOrderRepositoryMock.findPartialPaymentOrderByPspReference(PSP_REFERENCE))
                .thenReturn(null);
        testObj.cancelPartialPaymentOrder(PSP_REFERENCE);
        verify(baseStoreServiceMock, never()).getCurrentBaseStore();
    }

    @Test(expected = RuntimeException.class)
    public void cancelPartialPaymentOrder_shouldThrowOnApiException() throws Exception {
        when(adyenPartialPaymentOrderRepositoryMock.findPartialPaymentOrderByPspReference(PSP_REFERENCE))
                .thenReturn(partialPaymentOrderModelMock);
        when(ordersApiMock.cancelOrder(any(CancelOrderRequest.class)))
                .thenThrow(new ApiException("API Error", 500));
        testObj.cancelPartialPaymentOrder(PSP_REFERENCE);
    }

    @Test(expected = RuntimeException.class)
    public void cancelPartialPaymentOrder_shouldThrowOnIOException() throws Exception {
        when(adyenPartialPaymentOrderRepositoryMock.findPartialPaymentOrderByPspReference(PSP_REFERENCE))
                .thenReturn(partialPaymentOrderModelMock);
        when(ordersApiMock.cancelOrder(any(CancelOrderRequest.class)))
                .thenThrow(new IOException("IO Error"));
        testObj.cancelPartialPaymentOrder(PSP_REFERENCE);
    }
}
