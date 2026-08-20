package com.adyen.v6.service;

import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import com.adyen.model.checkout.PaymentDetailsResponse;
import com.adyen.v6.event.AdyenPaymentAuthorizedEvent;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.servicelayer.event.EventService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.tx.Transaction;

@UnitTest
public class DefaultThreeDSAuthorizationServiceTest
{
    private TestableThreeDSAuthorizationService service;
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

        service = new TestableThreeDSAuthorizationService();
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
        sequence.verify(orderService).updatePaymentInfo((AbstractOrderModel) order, "", additionalData);
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

    /**
     * The listeners read the token back from the database on another thread, so publishing while the
     * transaction that wrote it is still open is the bug this guards against.
     */
    @Test
    public void holdsTheAuthorizedEventBackUntilTheTransactionCommits() throws Exception
    {
        final OrderModel order = mock(OrderModel.class);
        service.transaction = mock(Transaction.class);

        service.publishAuthorizedEvent(order);

        verify(eventService, never()).publishEvent(any(AdyenPaymentAuthorizedEvent.class));

        final ArgumentCaptor<Transaction.TransactionAwareExecution> onCommit =
                ArgumentCaptor.forClass(Transaction.TransactionAwareExecution.class);
        verify(service.transaction).executeOnCommit(onCommit.capture());
        onCommit.getValue().execute(service.transaction);

        final ArgumentCaptor<AdyenPaymentAuthorizedEvent> published =
                ArgumentCaptor.forClass(AdyenPaymentAuthorizedEvent.class);
        verify(eventService).publishEvent(published.capture());
        assertSame(order, published.getValue().getOrder());
    }

    @Test
    public void publishesImmediatelyWhenNoTransactionIsRunning()
    {
        final OrderModel order = mock(OrderModel.class);
        service.transaction = null;

        service.publishAuthorizedEvent(order);

        verify(eventService).publishEvent(any(AdyenPaymentAuthorizedEvent.class));
    }

    /**
     * Supplies the transaction rather than letting the service ask the platform for it: with no booted
     * platform there is no transaction factory for {@code Transaction.current()} to use. A null one means
     * "no transaction is running", which leaves the tests that are not about transactions on the
     * immediate-publish path.
     */
    private static class TestableThreeDSAuthorizationService extends DefaultThreeDSAuthorizationService
    {
        private Transaction transaction;

        @Override
        protected Transaction runningTransaction()
        {
            return transaction;
        }
    }
}
