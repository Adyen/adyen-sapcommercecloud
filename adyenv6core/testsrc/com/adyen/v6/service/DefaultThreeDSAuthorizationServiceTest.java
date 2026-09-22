package com.adyen.v6.service;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import com.adyen.model.checkout.PaymentDetailsResponse;
import com.adyen.v6.event.AdyenPaymentAuthorizedEventPublisher;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.servicelayer.model.ModelService;

/**
 * Which 3DS/redirect outcomes announce an authorization. When the announcement actually reaches the
 * listeners is {@link AdyenPaymentAuthorizedEventPublisher}'s question, and is tested there.
 */
@UnitTest
public class DefaultThreeDSAuthorizationServiceTest
{
    private DefaultThreeDSAuthorizationService service;
    private ModelService modelService;
    private AdyenTransactionService transactionService;
    private AdyenOrderService orderService;
    private AdyenBusinessProcessService businessProcessService;
    private AdyenPaymentAuthorizedEventPublisher publisher;

    @Before
    public void setUp()
    {
        modelService = mock(ModelService.class);
        transactionService = mock(AdyenTransactionService.class);
        orderService = mock(AdyenOrderService.class);
        businessProcessService = mock(AdyenBusinessProcessService.class);
        publisher = mock(AdyenPaymentAuthorizedEventPublisher.class);

        service = new DefaultThreeDSAuthorizationService();
        service.setModelService(modelService);
        service.setAdyenTransactionService(transactionService);
        service.setAdyenOrderService(orderService);
        service.setAdyenBusinessProcessService(businessProcessService);
        service.setAdyenPaymentAuthorizedEventPublisher(publisher);
    }

    /**
     * The order has to carry its token before anyone is told the payment was authorized, or a listener
     * reading the PaymentInfo back finds it half written.
     */
    @Test
    public void announcesTheAuthorizationOnlyAfterPaymentInfoIsStored()
    {
        final OrderModel order = mock(OrderModel.class);
        final PaymentDetailsResponse response = mock(PaymentDetailsResponse.class);
        final Map<String, String> additionalData = Map.of("networkTxReference", "NTID-42");
        when(order.getCode()).thenReturn("00060001");
        when(response.getResultCode()).thenReturn(PaymentDetailsResponse.ResultCodeEnum.AUTHORISED);
        when(response.getAdditionalData()).thenReturn(additionalData);

        service.updateOrderPaymentStatusAndInfo(order, response);

        final InOrder sequence = inOrder(orderService, publisher);
        sequence.verify(orderService).updatePaymentInfo((AbstractOrderModel) order, "", additionalData);
        sequence.verify(publisher).publishAuthorized(order);
    }

    @Test
    public void announcesNothingForARefusedPayment()
    {
        final OrderModel order = mock(OrderModel.class);
        final PaymentDetailsResponse response = mock(PaymentDetailsResponse.class);
        when(order.getCode()).thenReturn("00060001");
        when(response.getResultCode()).thenReturn(PaymentDetailsResponse.ResultCodeEnum.REFUSED);

        service.updateOrderPaymentStatusAndInfo(order, response);

        verify(publisher, never()).publishAuthorized(order);
    }

    /**
     * RECEIVED means Adyen has taken the payment on but has not confirmed it. The order is left pending and
     * the confirmation arrives later as a notification, so nothing may be announced as authorized here.
     */
    @Test
    public void announcesNothingForAPaymentOnlyReceived()
    {
        final OrderModel order = mock(OrderModel.class);
        final PaymentDetailsResponse response = mock(PaymentDetailsResponse.class);
        when(order.getCode()).thenReturn("00060001");
        when(response.getResultCode()).thenReturn(PaymentDetailsResponse.ResultCodeEnum.RECEIVED);

        service.updateOrderPaymentStatusAndInfo(order, response);

        verify(publisher, never()).publishAuthorized(order);
    }
}
