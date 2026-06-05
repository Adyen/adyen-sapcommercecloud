package com.adyen.v6.security;

import com.adyen.model.notification.NotificationRequest;
import com.adyen.model.notification.NotificationRequestItem;
import com.adyen.util.HMACValidator;
import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.store.BaseStoreModel;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.SignatureException;
import java.util.Base64;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class AdyenNotificationAuthenticationProviderTest {

    private static final String HMAC_KEY = "9064450A8892A093D9E97EFCC9639DE31B74F3A7803135555A3C96F5A57915D6";
    private static final String USERNAME = "notif-user";
    private static final String PASSWORD = "notif-pass";
    private static final String BASE_SITE_ID = "electronics";
    private static final String REQUEST_BODY = "{\"notificationItems\":[]}";

    @Mock
    private HttpServletRequest request;

    @Mock
    private BaseStoreModel baseStore;

    @Mock
    private HMACValidator hmacValidator;

    private AdyenNotificationAuthenticationProvider provider;

    @Before
    public void setUp() {
        provider = spy(new AdyenNotificationAuthenticationProvider());
    }

    private void givenBaseStoreResolves() {
        doReturn(baseStore).when(provider).getBaseStore(BASE_SITE_ID);
    }

    private void givenHmacValidator() {
        doReturn(hmacValidator).when(provider).getHMACValidator();
    }

    private void givenConfiguredCredentials() {
        when(baseStore.getAdyenNotificationUsername()).thenReturn(USERNAME);
        when(baseStore.getAdyenNotificationPassword()).thenReturn(PASSWORD);
    }

    private void givenBasicAuthHeader(String username, String password) {
        String credentials = username + ":" + password;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        when(request.getHeader("Authorization")).thenReturn("Basic " + encoded);
    }

    private NotificationRequest notificationRequestWithOneItem() {
        NotificationRequestItem item = new NotificationRequestItem();
        item.setPspReference("pspRef");
        item.setEventCode("AUTHORISATION");
        item.setSuccess(true);
        NotificationRequest notificationRequest = new NotificationRequest();
        notificationRequest.setNotificationItems(Collections.singletonList(item));
        return notificationRequest;
    }

    // --- Empty HMAC key behaviour (the security fix) ---

    @Test
    public void emptyKeyWithBypassDisabledIsRejected_header() {
        when(baseStore.getAdyenNotificationHMACKey()).thenReturn("");
        when(baseStore.getAdyenAllowEmptyHMACKey()).thenReturn(false);

        assertFalse(provider.checkHMACFromHeader(request, REQUEST_BODY, baseStore));
    }

    @Test
    public void emptyKeyWithBypassEnabledIsAccepted_header() {
        when(baseStore.getAdyenNotificationHMACKey()).thenReturn("");
        when(baseStore.getAdyenAllowEmptyHMACKey()).thenReturn(true);

        assertTrue(provider.checkHMACFromHeader(request, REQUEST_BODY, baseStore));
    }

    @Test
    public void emptyKeyWithBypassDisabledIsRejected_additionalData() {
        when(baseStore.getAdyenNotificationHMACKey()).thenReturn("");
        when(baseStore.getAdyenAllowEmptyHMACKey()).thenReturn(false);

        assertFalse(provider.checkHMACFromAdditionalData(new NotificationRequest(), baseStore));
    }

    @Test
    public void emptyKeyWithBypassEnabledIsAccepted_additionalData() {
        when(baseStore.getAdyenNotificationHMACKey()).thenReturn("");
        when(baseStore.getAdyenAllowEmptyHMACKey()).thenReturn(true);

        assertTrue(provider.checkHMACFromAdditionalData(new NotificationRequest(), baseStore));
    }

    @Test
    public void nullBypassFlagIsTreatedAsRejected() {
        when(baseStore.getAdyenAllowEmptyHMACKey()).thenReturn(null);

        assertFalse(provider.allowEmptyHMACKey(baseStore));
    }

    // --- Configured HMAC key still validates signatures ---

    @Test
    public void validSignatureIsAccepted_header() throws Exception {
        givenHmacValidator();
        when(baseStore.getAdyenNotificationHMACKey()).thenReturn(HMAC_KEY);
        when(request.getHeader("hmacsignature")).thenReturn("some-signature");
        when(hmacValidator.validateHMAC("some-signature", HMAC_KEY, REQUEST_BODY)).thenReturn(true);

        assertTrue(provider.checkHMACFromHeader(request, REQUEST_BODY, baseStore));
    }

    @Test
    public void invalidSignatureIsRejected_header() throws Exception {
        givenHmacValidator();
        when(baseStore.getAdyenNotificationHMACKey()).thenReturn(HMAC_KEY);
        when(request.getHeader("hmacsignature")).thenReturn("bad-signature");
        when(hmacValidator.validateHMAC("bad-signature", HMAC_KEY, REQUEST_BODY)).thenReturn(false);

        assertFalse(provider.checkHMACFromHeader(request, REQUEST_BODY, baseStore));
    }

    @Test
    public void signatureExceptionIsRejected_header() throws Exception {
        givenHmacValidator();
        when(baseStore.getAdyenNotificationHMACKey()).thenReturn(HMAC_KEY);
        when(request.getHeader("hmacsignature")).thenReturn("bad-signature");
        when(hmacValidator.validateHMAC(anyString(), anyString(), anyString()))
                .thenThrow(new SignatureException("boom"));

        assertFalse(provider.checkHMACFromHeader(request, REQUEST_BODY, baseStore));
    }

    @Test
    public void validSignatureIsAccepted_additionalData() throws Exception {
        givenHmacValidator();
        when(baseStore.getAdyenNotificationHMACKey()).thenReturn(HMAC_KEY);
        when(hmacValidator.validateHMAC(any(NotificationRequestItem.class), eq(HMAC_KEY))).thenReturn(true);

        assertTrue(provider.checkHMACFromAdditionalData(notificationRequestWithOneItem(), baseStore));
    }

    @Test
    public void tamperedSignatureIsRejected_additionalData() throws Exception {
        givenHmacValidator();
        when(baseStore.getAdyenNotificationHMACKey()).thenReturn(HMAC_KEY);
        when(hmacValidator.validateHMAC(any(NotificationRequestItem.class), eq(HMAC_KEY))).thenReturn(false);

        assertFalse(provider.checkHMACFromAdditionalData(notificationRequestWithOneItem(), baseStore));
    }

    // --- End-to-end authenticate(): basic auth AND hmac must both hold ---

    @Test
    public void authenticateRejectsForgedWebhookWhenKeyMissingAndBypassDisabled() {
        givenBaseStoreResolves();
        givenConfiguredCredentials();
        givenBasicAuthHeader(USERNAME, PASSWORD);
        when(baseStore.getAdyenNotificationHMACKey()).thenReturn("");
        when(baseStore.getAdyenAllowEmptyHMACKey()).thenReturn(false);

        assertFalse(provider.authenticate(request, REQUEST_BODY, BASE_SITE_ID));
    }

    @Test
    public void authenticateFailsWhenBasicAuthMissing() {
        givenBaseStoreResolves();
        when(request.getHeader("Authorization")).thenReturn(null);

        assertFalse(provider.authenticate(request, REQUEST_BODY, BASE_SITE_ID));
    }

    @Test
    public void authenticateFailsWhenBasicAuthWrongPassword() {
        givenBaseStoreResolves();
        givenConfiguredCredentials();
        givenBasicAuthHeader(USERNAME, "wrong");

        assertFalse(provider.authenticate(request, REQUEST_BODY, BASE_SITE_ID));
    }

    @Test
    public void authenticateSucceedsWithValidBasicAuthAndSignature() throws Exception {
        givenBaseStoreResolves();
        givenConfiguredCredentials();
        givenBasicAuthHeader(USERNAME, PASSWORD);
        givenHmacValidator();
        when(baseStore.getAdyenNotificationHMACKey()).thenReturn(HMAC_KEY);
        when(request.getHeader("hmacsignature")).thenReturn("some-signature");
        when(hmacValidator.validateHMAC("some-signature", HMAC_KEY, REQUEST_BODY)).thenReturn(true);

        assertTrue(provider.authenticate(request, REQUEST_BODY, BASE_SITE_ID));
    }
}
