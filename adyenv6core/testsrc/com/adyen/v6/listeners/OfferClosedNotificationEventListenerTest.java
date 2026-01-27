package com.adyen.v6.listeners;

import com.adyen.v6.events.OfferClosedEvent;
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
import org.mockito.runners.MockitoJUnitRunner;

import java.util.ArrayList;

import static com.adyen.model.notification.NotificationRequestItem.EVENT_CODE_CAPTURE;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class OfferClosedNotificationEventListenerTest {

    @Mock
    private ModelService modelServiceMock;

    @Mock
    private DefaultAdyenNotificationService adyenNotificationService;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepositoryMock;

    @InjectMocks
    private OfferClosedNotificationEventListener offerClosedNotificationEventListener;

    @Test
    public void testOnEvent() {

        PaymentTransactionModel paymentTransactionModel = new PaymentTransactionModel();
        paymentTransactionModel.setEntries(new ArrayList<>());

        when(paymentTransactionRepositoryMock.getTransactionModel(Mockito.any(String.class))).thenReturn(paymentTransactionModel);

        AdyenNotificationModel adyenNotificationModel = new AdyenNotificationModel();
        adyenNotificationModel.setPspReference("123");
        adyenNotificationModel.setOriginalReference("123");
        adyenNotificationModel.setEventCode(EVENT_CODE_CAPTURE);
        adyenNotificationModel.setSuccess(true);

        OfferClosedEvent offerClosedEvent = new OfferClosedEvent(adyenNotificationModel);

        offerClosedNotificationEventListener.onEvent(offerClosedEvent);

        verify(adyenNotificationService).processCancelEvent(adyenNotificationModel, paymentTransactionModel);
        verify(modelServiceMock).save(adyenNotificationModel);

    }
}