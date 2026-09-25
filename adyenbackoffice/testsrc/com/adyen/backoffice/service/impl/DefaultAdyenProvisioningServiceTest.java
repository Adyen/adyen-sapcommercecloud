package com.adyen.backoffice.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adyen.backoffice.dto.ProvisionReportWsDTO;
import com.adyen.backoffice.dto.ProvisionRequestWsDTO;
import com.adyen.backoffice.dto.ProvisionStepWsDTO;
import com.adyen.backoffice.service.AdyenSetupService;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.store.BaseStoreModel;

@UnitTest
public class DefaultAdyenProvisioningServiceTest {

    @Mock
    private ModelService modelService;
    @Mock
    private AdyenSetupService adyenSetupService;

    private final Map<String, Map<String, Object>> answers = new HashMap<>();
    private final List<String> calls = new ArrayList<>();
    private DefaultAdyenProvisioningService service;
    private BaseStoreModel store;
    private ProvisionRequestWsDTO request;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        answers.clear();
        calls.clear();

        service = new DefaultAdyenProvisioningService() {
            @Override
            protected Map<String, Object> post(final String path, final Map<String, Object> body,
                    final String managementKey) {
                calls.add(path);
                // Longest match wins: the HMAC path also contains "/webhooks", and map order is not a
                // thing to rely on.
                String best = null;
                for (final String key : answers.keySet()) {
                    if (path.contains(key) && (best == null || key.length() > best.length())) {
                        best = key;
                    }
                }
                return best == null ? null : answers.get(best);
            }
        };
        service.setModelService(modelService);
        service.setAdyenSetupService(adyenSetupService);

        store = mock(BaseStoreModel.class);
        when(store.getUid()).thenReturn("electronics");
        when(adyenSetupService.apiKeyFor(store)).thenReturn("mgmt-key");

        request = new ProvisionRequestWsDTO();
        request.setBaseStore("electronics");
        request.setMerchantAccount("TestMerchant");
        request.setStorefrontOrigin("https://shop.example.com");
        request.setNotificationUrl("https://shop.example.com/adyen/v6/notification/electronics/json");

        answers.put("generateHmac", Map.of("hmacKey", "HMAC123"));
        answers.put("/webhooks", Map.of("id", "WH1"));
        answers.put("/apiCredentials", Map.of(
                "apiKey", "AQE-checkout", "clientKey", "test_CLIENT", "username", "ws@Company.Test"));
    }

    @Test
    public void createsCredentialWebhookAndHmacAndRecordsThemOnTheStore() {
        final ProvisionReportWsDTO report = service.provision(store, request);

        assertTrue(report.isComplete());
        verify(store).setAdyenMerchantAccount("TestMerchant");
        verify(store).setAdyenAPIKey("AQE-checkout");
        verify(store).setAdyenClientKey("test_CLIENT");
        verify(store).setAdyenNotificationHMACKey("HMAC123");
        assertEquals(4, report.getSteps().size());
        assertTrue(report.getSteps().stream().allMatch(ProvisionStepWsDTO::isDone));
    }

    /**
     * Adyen returns a new API key exactly once. It must therefore be persisted before anything that could
     * fail is attempted, or a webhook failure would take the key down with it.
     */
    @Test
    public void savesTheApiKeyBeforeAttemptingTheWebhook() {
        service.provision(store, request);

        final InOrder order = inOrder(store, modelService);
        order.verify(store).setAdyenAPIKey("AQE-checkout");
        order.verify(modelService).save(store);
        order.verify(store).setAdyenNotificationUsername(any());
    }

    @Test
    public void keepsTheApiKeyEvenWhenTheWebhookStepFails() {
        answers.remove("/webhooks");

        final ProvisionReportWsDTO report = service.provision(store, request);

        assertFalse(report.isComplete());
        // The credential happened and is recorded; only the webhook is outstanding.
        verify(store).setAdyenAPIKey("AQE-checkout");
        verify(store, never()).setAdyenNotificationHMACKey(any());
        assertTrue(report.getSteps().stream().anyMatch(s -> "credential".equals(s.getName()) && s.isDone()));
        assertTrue(report.getSteps().stream().anyMatch(s -> "webhook".equals(s.getName()) && !s.isDone()));
    }

    /**
     * A webhook without an HMAC key is worse than no webhook: notifications arrive and are rejected. The
     * run must not be reported as complete.
     */
    @Test
    public void refusesToCallTheRunCompleteWhenTheHmacKeyIsMissing() {
        answers.put("generateHmac", Map.of());

        final ProvisionReportWsDTO report = service.provision(store, request);

        assertFalse(report.isComplete());
        verify(store, never()).setAdyenNotificationHMACKey(any());
    }

    @Test
    public void stopsBeforeTheWebhookWhenTheCredentialCannotBeCreated() {
        answers.remove("/apiCredentials");

        final ProvisionReportWsDTO report = service.provision(store, request);

        assertFalse(report.isComplete());
        assertFalse(calls.stream().anyMatch(c -> c.contains("/webhooks")));
        verify(store, never()).setAdyenAPIKey(any());
    }

    @Test
    public void doesNothingWithoutAManagementKey() {
        when(adyenSetupService.apiKeyFor(store)).thenReturn(null);

        assertFalse(service.provision(store, request).isComplete());
        assertTrue(calls.isEmpty());
    }

    /** Whatever the wizard creates is stored, never handed back to the browser. */
    @Test
    public void reportsNoSecretsBack() {
        final ProvisionReportWsDTO report = service.provision(store, request);

        for (final ProvisionStepWsDTO step : report.getSteps()) {
            final String detail = step.getDetail() == null ? "" : step.getDetail();
            assertFalse(detail.contains("AQE-checkout"));
            assertFalse(detail.contains("HMAC123"));
            assertFalse(detail.contains("test_CLIENT"));
        }
    }

    /** Each webhook password is generated, not fixed, and long enough to be worth generating. */
    @Test
    public void generatesADistinctWebhookPasswordEachTime() {
        final String first = service.newPassword();
        final String second = service.newPassword();

        assertFalse(first.equals(second));
        assertTrue(first.length() >= 32);
    }
}
