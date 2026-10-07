package com.adyen.v6.service.impl;

import com.adyen.model.notification.NotificationRequest;
import com.adyen.model.notification.NotificationRequestItem;
import com.adyen.v6.events.DonationEvent;
import com.adyen.v6.model.AdyenNotificationModel;
import com.adyen.v6.repository.AdyenNotificationRepository;
import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.servicelayer.event.EventService;
import de.hybris.platform.servicelayer.model.ModelService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class DefaultAdyenNotificationV2ServiceTest {
    private static final String DONATION = "DONATION";

    @Mock
    private ModelService modelService;
    @Mock
    private EventService eventService;
    @Mock
    private AdyenNotificationRepository adyenNotificationRepository;
    @Mock
    private AdyenNotificationModel notificationModel;

    private DefaultAdyenNotificationV2Service service;

    @Before
    public void setUp() {
        service = new DefaultAdyenNotificationV2Service();
        service.setModelService(modelService);
        service.setEventService(eventService);
        service.setAdyenNotificationRepository(adyenNotificationRepository);
    }

    @Test
    public void createsDonationEventForDonationNotification() {
        final AdyenNotificationModel notification = new AdyenNotificationModel();
        notification.setEventCode(DONATION);

        assertTrue(service.createEvent(notification).orElseThrow() instanceof DonationEvent);
    }

    @Test
    public void ignoresAlreadyProcessedDonationNotification() {
        final NotificationRequestItem item = donationNotification();
        final NotificationRequest request = new NotificationRequest();
        request.setNotificationItems(Collections.singletonList(item));
        when(adyenNotificationRepository.isProcessed("donation-psp", DONATION, true)).thenReturn(true);

        service.onRequest(request);

        verify(modelService, never()).save(any());
        verify(eventService, never()).publishEvent(any());
    }

    @Test
    public void persistsAndPublishesNewDonationNotification() {
        final NotificationRequestItem item = donationNotification();
        final NotificationRequest request = new NotificationRequest();
        request.setNotificationItems(Collections.singletonList(item));
        when(modelService.create(AdyenNotificationModel.class)).thenReturn(notificationModel);
        when(notificationModel.getEventCode()).thenReturn(DONATION);

        service.onRequest(request);

        verify(modelService).save(notificationModel);
        verify(eventService).publishEvent(any(DonationEvent.class));
    }

    private NotificationRequestItem donationNotification() {
        final NotificationRequestItem item = new NotificationRequestItem();
        item.setEventCode(DONATION);
        item.setPspReference("donation-psp");
        item.setSuccess(true);
        return item;
    }
}
