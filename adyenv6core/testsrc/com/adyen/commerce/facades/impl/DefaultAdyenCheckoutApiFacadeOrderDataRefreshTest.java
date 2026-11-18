package com.adyen.commerce.facades.impl;

import com.adyen.model.checkout.CheckoutOrderResponse;
import com.adyen.model.checkout.PaymentResponse;
import com.adyen.v6.model.AdyenPartialPaymentOrderModel;
import com.adyen.v6.repository.AdyenPartialPaymentOrderRepository;
import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.servicelayer.model.ModelService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.*;

/**
 * Adyen returns an updated order with every payment made against it; /orders/cancel needs the latest orderData,
 * so the stored one has to follow the gift card leg.
 */
@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class DefaultAdyenCheckoutApiFacadeOrderDataRefreshTest {

    private static final String PSP_REFERENCE = "GCB_001";

    @Spy
    @InjectMocks
    private DefaultAdyenCheckoutApiFacade testObj;

    @Mock
    private AdyenPartialPaymentOrderRepository adyenPartialPaymentOrderRepositoryMock;

    @Mock
    private ModelService modelServiceMock;

    @Mock
    private AdyenPartialPaymentOrderModel partialPaymentMock;

    @Before
    public void setUp() {
        doReturn(modelServiceMock).when(testObj).getModelService();
    }

    @Test
    public void refreshPartialPaymentOrderData_shouldStoreOrderDataFromPaymentResponse() {
        when(adyenPartialPaymentOrderRepositoryMock.findPartialPaymentOrderByPspReference(PSP_REFERENCE)).thenReturn(partialPaymentMock);
        final PaymentResponse response = new PaymentResponse().order(new CheckoutOrderResponse().orderData("NEW_ORDER_DATA"));

        testObj.refreshPartialPaymentOrderData(PSP_REFERENCE, response);

        verify(partialPaymentMock).setOrderData("NEW_ORDER_DATA");
        verify(modelServiceMock).save(partialPaymentMock);
    }

    @Test
    public void refreshPartialPaymentOrderData_shouldKeepStoredOrderDataWhenResponseHasNone() {
        testObj.refreshPartialPaymentOrderData(PSP_REFERENCE, new PaymentResponse());
        testObj.refreshPartialPaymentOrderData(PSP_REFERENCE, new PaymentResponse().order(new CheckoutOrderResponse()));

        verifyNoInteractions(adyenPartialPaymentOrderRepositoryMock);
        verifyNoInteractions(modelServiceMock);
    }
}
