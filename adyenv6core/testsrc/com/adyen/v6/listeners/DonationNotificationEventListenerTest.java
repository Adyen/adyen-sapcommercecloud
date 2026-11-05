package com.adyen.v6.listeners;

import com.adyen.v6.events.DonationEvent;
import com.adyen.v6.model.AdyenNotificationModel;
import com.adyen.v6.service.AdyenDonationReconciliationService;
import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.servicelayer.model.ModelService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class DonationNotificationEventListenerTest {

    @Mock
    private ModelService modelServiceMock;

    @Mock
    private AdyenDonationReconciliationService adyenDonationReconciliationService;

    @InjectMocks
    private DonationNotificationEventListener listener;

    @Test
    public void marksNotificationAsProcessedAfterReconciliation() {
        final AdyenNotificationModel notification = mock(AdyenNotificationModel.class);

        listener.onEvent(new DonationEvent(notification));

        verify(adyenDonationReconciliationService).reconcile(notification);
        verify(notification).setProcessedAt(any(Date.class));
        verify(modelServiceMock).save(notification);
    }

    @Test
    public void leavesNotificationUnprocessedWhenReconciliationFails() {
        final AdyenNotificationModel notification = mock(AdyenNotificationModel.class);
        doThrow(new IllegalStateException("missing data")).when(adyenDonationReconciliationService).reconcile(notification);

        listener.onEvent(new DonationEvent(notification));

        verify(notification, never()).setProcessedAt(any());
        verify(modelServiceMock, never()).save(notification);
    }
}
