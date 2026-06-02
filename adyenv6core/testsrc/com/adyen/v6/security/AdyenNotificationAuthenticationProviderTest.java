package com.adyen.v6.security;

import com.adyen.model.notification.Amount;
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
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
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

    private AdyenNotificationAuthenticationProvider provider;

    @Before
    public void setUp() {
        provider = spy(new AdyenNotificationAuthenticationProvider());
    }

    private void givenBaseStoreResolves() {
        doReturn(baseStore).when(provider).getBaseStore(BASE_SITE_ID);
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

    private NotificationRequest signedNotificationRequest(String key) throws Exception {
        NotificationRequestItem item = new NotificationRequestItem();
        item.setPspReference("pspRef");
        item.setOriginalReference("");
        item.setMerchantAccountCode("merchant");
        item.setMerchantReference("merchantRef");
        item.setEventCode("AUTHORISATION");
        item.setSuccess(true);
        Amount amount = new Amount();
        amount.setValue(1000L);
        amount.setCurrency("EUR");
        item.setAmount(amount);

        Map<String, String> additionalData = new HashMap<>();
        additionalData.put("hmacSignature", new HMACValidator().calculateHMAC(item, key));
        item.setAdditionalData(additionalData);

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
        when(baseStore.getAdyenNotificationHMACKey()).thenReturn(HMAC_KEY);
        String signature = new HMACValidator().calculateHMAC(REQUEST_BODY, HMAC_KEY);
        when(request.getHeader("hmacsignature")).thenReturn(signature);

        assertTrue(provider.checkHMACFromHeader(request, REQUEST_BODY, baseStore));
    }

    @Test
    public void invalidSignatureIsRejected_header() {
        when(baseStore.getAdyenNotificationHMACKey()).thenReturn(HMAC_KEY);
        when(request.getHeader("hmacsignature")).thenReturn("not-a-valid-signature");

        assertFalse(provider.checkHMACFromHeader(request, REQUEST_BODY, baseStore));
    }

    @Test
    public void validSignatureIsAccepted_additionalData() throws Exception {
        when(baseStore.getAdyenNotificationHMACKey()).thenReturn(HMAC_KEY);

        assertTrue(provider.checkHMACFromAdditionalData(signedNotificationRequest(HMAC_KEY), baseStore));
    }

    @Test
    public void tamperedSignatureIsRejected_additionalData() throws Exception {
        when(baseStore.getAdyenNotificationHMACKey()).thenReturn(HMAC_KEY);
        NotificationRequest tampered = signedNotificationRequest(HMAC_KEY);
        tampered.getNotificationItems().get(0).setMerchantReference("attacker-changed-this");

        assertFalse(provider.checkHMACFromAdditionalData(tampered, baseStore));
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
        when(baseStore.getAdyenNotificationHMACKey()).thenReturn(HMAC_KEY);
        String signature = new HMACValidator().calculateHMAC(REQUEST_BODY, HMAC_KEY);
        when(request.getHeader("hmacsignature")).thenReturn(signature);

        assertTrue(provider.authenticate(request, REQUEST_BODY, BASE_SITE_ID));
    }
}
