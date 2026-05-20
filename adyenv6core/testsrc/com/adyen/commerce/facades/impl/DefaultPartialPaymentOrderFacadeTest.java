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
    public void cancelPartialPaymentOrder_shouldSkipWhenPspReferenceIsNull() {
        testObj.cancelPartialPaymentOrder(null);
        verify(adyenPartialPaymentOrderRepositoryMock, never()).findPartialPaymentOrderByPspReference(any());
    }

    @Test
    public void cancelPartialPaymentOrder_shouldSkipWhenPspReferenceIsEmpty() {
        testObj.cancelPartialPaymentOrder("");
        verify(adyenPartialPaymentOrderRepositoryMock, never()).findPartialPaymentOrderByPspReference(any());
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
