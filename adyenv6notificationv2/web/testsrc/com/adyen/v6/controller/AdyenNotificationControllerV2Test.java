package com.adyen.v6.controller;

import com.adyen.model.notification.NotificationRequest;
import com.adyen.v6.security.AdyenNotificationAuthenticationProvider;
import com.adyen.v6.service.AdyenNotificationService;
import com.adyen.v6.service.AdyenNotificationV2Service;
import de.hybris.bootstrap.annotations.UnitTest;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class AdyenNotificationControllerV2Test {
    @Mock
    private HttpServletRequest request;
    @Mock
    private AdyenNotificationV2Service notificationV2Service;
    @Mock
    private AdyenNotificationAuthenticationProvider authenticationProvider;
    @Mock
    private AdyenNotificationService notificationService;

    private AdyenNotificationControllerV2 controller;
    private NotificationRequest notificationRequest;

    @Before
    public void setUp() throws Exception {
        controller = new AdyenNotificationControllerV2();
        notificationRequest = new NotificationRequest();
        setField("adyenNotificationV2Service", notificationV2Service);
        setField("adyenNotificationAuthenticationProvider", authenticationProvider);
        setField("adyenNotificationService", notificationService);
        when(request.getInputStream()).thenReturn(inputStream("{\"notificationItems\":[]}"));
        when(notificationService.getNotificationRequestFromString(any())).thenReturn(notificationRequest);
    }

    @Test
    public void rejectsWebhookWhenHmacValidationFails() {
        when(authenticationProvider.authenticate(eq(request), eq(notificationRequest), eq("electronics"))).thenReturn(false);

        assertEquals("[not-accepted]", controller.onReceive("electronics", request));

        verify(notificationV2Service, never()).onRequest(any());
    }

    @Test
    public void acceptsWebhookOnlyAfterAuthenticationSucceeds() {
        when(authenticationProvider.authenticate(eq(request), eq(notificationRequest), eq("electronics"))).thenReturn(true);

        assertEquals("[accepted]", controller.onReceive("electronics", request));

        verify(notificationV2Service).onRequest(notificationRequest);
    }

    private void setField(final String fieldName, final Object value) throws Exception {
        final Field field = AdyenNotificationControllerV2.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(controller, value);
    }

    private ServletInputStream inputStream(final String payload) {
        final ByteArrayInputStream delegate = new ByteArrayInputStream(payload.getBytes());
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return delegate.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(final ReadListener readListener) {
                // Synchronous test input stream.
            }

            @Override
            public int read() throws IOException {
                return delegate.read();
            }
        };
    }
}
