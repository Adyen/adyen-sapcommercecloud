package com.adyen.v6.service;

import static org.mockito.ArgumentMatchers.any;
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
import com.adyen.v6.event.AdyenPaymentAuthorizedEvent;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.servicelayer.event.EventService;
import de.hybris.platform.servicelayer.model.ModelService;

@UnitTest
public class DefaultThreeDSAuthorizationServiceTest
{
    private DefaultThreeDSAuthorizationService service;
    private ModelService modelService;
    private AdyenTransactionService transactionService;
    private AdyenOrderService orderService;
    private AdyenBusinessProcessService businessProcessService;
    private EventService eventService;

    @Before
    public void setUp()
    {
        modelService = mock(ModelService.class);
        transactionService = mock(AdyenTransactionService.class);
        orderService = mock(AdyenOrderService.class);
        businessProcessService = mock(AdyenBusinessProcessService.class);
        eventService = mock(EventService.class);

        service = new DefaultThreeDSAuthorizationService();
        service.setModelService(modelService);
        service.setAdyenTransactionService(transactionService);
        service.setAdyenOrderService(orderService);
        service.setAdyenBusinessProcessService(businessProcessService);
        service.setEventService(eventService);
    }

    @Test
    public void publishesAuthorizedEventAfterPaymentInfoIsStored()
    {
        final OrderModel order = mock(OrderModel.class);
        final PaymentDetailsResponse response = mock(PaymentDetailsResponse.class);
        final Map<String, String> additionalData = Map.of("networkTxReference", "NTID-42");
        when(order.getCode()).thenReturn("00060001");
        when(response.getResultCode()).thenReturn(PaymentDetailsResponse.ResultCodeEnum.AUTHORISED);
        when(response.getAdditionalData()).thenReturn(additionalData);

        service.updateOrderPaymentStatusAndInfo(order, response);

        final InOrder sequence = inOrder(orderService, eventService);
        sequence.verify(orderService).updatePaymentInfo(order, "", additionalData);
        sequence.verify(eventService).publishEvent(any(AdyenPaymentAuthorizedEvent.class));
    }

    @Test
    public void doesNotPublishAuthorizedEventForRefusedPayment()
    {
        final OrderModel order = mock(OrderModel.class);
        final PaymentDetailsResponse response = mock(PaymentDetailsResponse.class);
        when(order.getCode()).thenReturn("00060001");
        when(response.getResultCode()).thenReturn(PaymentDetailsResponse.ResultCodeEnum.REFUSED);

        service.updateOrderPaymentStatusAndInfo(order, response);

        verify(eventService, never()).publishEvent(any(AdyenPaymentAuthorizedEvent.class));
    }
}
