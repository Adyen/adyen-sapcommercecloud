package com.adyen.v6.listeners;

import com.adyen.v6.events.CancelOrRefundEvent;
import com.adyen.v6.events.CaptureEvent;
import com.adyen.v6.model.AdyenNotificationModel;
import com.adyen.v6.repository.PaymentTransactionRepository;
import com.adyen.v6.service.DefaultAdyenNotificationService;
import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.payment.model.PaymentTransactionModel;
import de.hybris.platform.servicelayer.model.ModelService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;

import static com.adyen.model.notification.NotificationRequestItem.EVENT_CODE_CANCEL_OR_REFUND;
import static com.adyen.model.notification.NotificationRequestItem.EVENT_CODE_CAPTURE;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class CancelOrRefundNotificationEventListenerTest {

    @Mock
    private ModelService modelServiceMock;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepositoryMock;

    @Mock
    private DefaultAdyenNotificationService adyenNotificationService;

    @InjectMocks
    private CancelOrRefundNotificationEventListener cancelOrRefundNotificationEventListener;

    @Test
    public void testOnEvent() {

        AdyenNotificationModel adyenNotificationModel = new AdyenNotificationModel();
        adyenNotificationModel.setPspReference("123");
        adyenNotificationModel.setOriginalReference("123");
        adyenNotificationModel.setEventCode(EVENT_CODE_CANCEL_OR_REFUND);
        adyenNotificationModel.setSuccess(true);

        PaymentTransactionModel paymentTransactionModel = new PaymentTransactionModel();
        paymentTransactionModel.setEntries(new ArrayList<>());

        when(paymentTransactionRepositoryMock.getTransactionModel(Mockito.any(String.class))).thenReturn(paymentTransactionModel);

        CancelOrRefundEvent cancelOrRefundEvent = new CancelOrRefundEvent(adyenNotificationModel);

        cancelOrRefundNotificationEventListener.onEvent(cancelOrRefundEvent);

        verify(adyenNotificationService).processCancelEvent(adyenNotificationModel,paymentTransactionModel);
        verify(modelServiceMock).save(adyenNotificationModel);

    }
}