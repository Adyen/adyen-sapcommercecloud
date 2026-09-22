package com.adyen.v6.listeners;

import com.adyen.v6.events.RefundEvent;
import com.adyen.v6.model.AdyenNotificationModel;
import com.adyen.v6.service.DefaultAdyenNotificationService;
import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.servicelayer.model.ModelService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static com.adyen.model.notification.NotificationRequestItem.EVENT_CODE_CAPTURE;
import static org.mockito.Mockito.verify;

@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class RefundNotificationEventListenerTest {

    @Mock
    private ModelService modelServiceMock;

    @Mock
    private DefaultAdyenNotificationService adyenNotificationService;

    @InjectMocks
    private RefundNotificationEventListener refundNotificationEventListener;

    @Test
    public void testOnEvent() {

        AdyenNotificationModel adyenNotificationModel = new AdyenNotificationModel();
        adyenNotificationModel.setPspReference("123");
        adyenNotificationModel.setOriginalReference("123");
        adyenNotificationModel.setEventCode(EVENT_CODE_CAPTURE);
        adyenNotificationModel.setSuccess(true);

        RefundEvent refundEvent = new RefundEvent(adyenNotificationModel);

        refundNotificationEventListener.onEvent(refundEvent);

        verify(adyenNotificationService).processRefundEvent(adyenNotificationModel);
        verify(modelServiceMock).save(adyenNotificationModel);

    }
}