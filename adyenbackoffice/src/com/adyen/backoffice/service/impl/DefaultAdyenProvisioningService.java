package com.adyen.backoffice.service.impl;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.adyen.backoffice.dto.ProvisionReportWsDTO;
import com.adyen.backoffice.dto.ProvisionRequestWsDTO;
import com.adyen.backoffice.service.AdyenProvisioningService;
import com.adyen.backoffice.service.AdyenSetupService;

import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.store.BaseStoreModel;

/**
 * Default implementation. Runs the four Adyen calls the plugin's configuration is made of, saving after
 * each one.
 *
 * <p>Saving as it goes rather than at the end is deliberate and not a convenience: Adyen returns a newly
 * created API key exactly once - "You won't be able to retrieve it later" - so a key held in memory until
 * a later step succeeds is a key lost when that step fails.</p>
 */
public class DefaultAdyenProvisioningService implements AdyenProvisioningService {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultAdyenProvisioningService.class);

    private static final String ENDPOINT_PROPERTY = "adyen.management.api.endpoint";
    /** Used when the installation has not set the property, so a fresh one reaches Adyen at all. */
    private static final String DEFAULT_ENDPOINT = "https://management-test.adyen.com/v3";
    private static final String X_API_KEY = "X-API-Key";

    /** What the storefront's credential is for. Payments only; it never administers the account. */
    private static final List<String> CHECKOUT_ROLES = List.of("Checkout webservice role");

    private static final String STEP_CREDENTIAL = "credential";
    private static final String STEP_ORIGIN = "allowedOrigin";
    private static final String STEP_WEBHOOK = "webhook";
    private static final String STEP_HMAC = "hmac";

    private ConfigurationService configurationService;
    private ModelService modelService;
    private AdyenSetupService adyenSetupService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final SecureRandom random = new SecureRandom();

    @Override
    public ProvisionReportWsDTO provision(final BaseStoreModel store, final ProvisionRequestWsDTO request) {
        final ProvisionReportWsDTO report = new ProvisionReportWsDTO();
        final String managementKey = adyenSetupService.apiKeyFor(store);
        if (StringUtils.isBlank(managementKey)) {
            report.add(STEP_CREDENTIAL, false, "This store has no Management API key yet.");
            return report;
        }
        final String merchantAccount = request.getMerchantAccount();

        store.setAdyenMerchantAccount(merchantAccount);

        if (!createCheckoutCredential(store, request, managementKey, report)) {
            return report;
        }
        final String webhookId = createWebhook(store, request, managementKey, report);
        if (webhookId == null) {
            return report;
        }
        if (!generateHmac(store, merchantAccount, webhookId, managementKey, report)) {
            return report;
        }
        report.setComplete(true);
        return report;
    }

    /**
     * Creates the Checkout credential and stores what came back before anything else is attempted.
     *
     * <p>The allowed origin travels in the same request, so the Drop-in's client key is usable from the
     * storefront the moment the credential exists.</p>
     */
    protected boolean createCheckoutCredential(final BaseStoreModel store,
            final ProvisionRequestWsDTO request, final String managementKey,
            final ProvisionReportWsDTO report) {
        final Map<String, Object> body = Map.of(
                "description", "SAP Commerce storefront - " + store.getUid(),
                "roles", CHECKOUT_ROLES,
                "allowedOrigins", List.of(request.getStorefrontOrigin()));

        final Map<String, Object> created = post(
                "/merchants/" + request.getMerchantAccount() + "/apiCredentials", body, managementKey);
        if (created == null) {
            // The ceiling is the company credential, not the key we called with: Adyen only lets a role be
            // assigned if ws@Company.<CompanyName> already holds it.
            report.add(STEP_CREDENTIAL, false, "Adyen refused to create the credential. Check that "
                    + "ws@Company.<CompanyName> holds the 'Checkout webservice role'.");
            return false;
        }

        final String apiKey = (String) created.get("apiKey");
        final String clientKey = (String) created.get("clientKey");
        if (StringUtils.isBlank(apiKey)) {
            report.add(STEP_CREDENTIAL, false, "Adyen created the credential but returned no API key.");
            return false;
        }

        store.setAdyenAPIKey(apiKey);
        store.setAdyenClientKey(clientKey);
        // Saved here and not at the end: Adyen returns the API key once and never again.
        modelService.save(store);

        report.add(STEP_CREDENTIAL, true, "Created API credential " + created.get("username") + ".");
        report.add(STEP_ORIGIN, true, "Registered " + request.getStorefrontOrigin() + " as an allowed origin.");
        return true;
    }

    protected String createWebhook(final BaseStoreModel store, final ProvisionRequestWsDTO request,
            final String managementKey, final ProvisionReportWsDTO report) {
        final String username = "sapcc-" + store.getUid();
        final String password = newPassword();

        final Map<String, Object> body = Map.of(
                "type", "standard",
                "communicationFormat", "json",
                "url", request.getNotificationUrl(),
                "active", Boolean.TRUE,
                "username", username,
                "password", password,
                "description", "SAP Commerce - " + store.getUid());

        final Map<String, Object> created = post(
                "/merchants/" + request.getMerchantAccount() + "/webhooks", body, managementKey);
        if (created == null) {
            report.add(STEP_WEBHOOK, false, "Adyen refused to create the webhook. Check that the key holds "
                    + "'Management API-Webhooks read and write'.");
            return null;
        }

        store.setAdyenNotificationUsername(username);
        store.setAdyenNotificationPassword(password);
        modelService.save(store);

        report.add(STEP_WEBHOOK, true, "Created webhook to " + request.getNotificationUrl() + ".");
        return (String) created.get("id");
    }

    protected boolean generateHmac(final BaseStoreModel store, final String merchantAccount,
            final String webhookId, final String managementKey, final ProvisionReportWsDTO report) {
        final Map<String, Object> created = post(
                "/merchants/" + merchantAccount + "/webhooks/" + webhookId + "/generateHmac",
                Map.of(), managementKey);
        final String hmacKey = created == null ? null : (String) created.get("hmacKey");
        if (StringUtils.isBlank(hmacKey)) {
            // The webhook exists but cannot be verified, which is worse than no webhook: notifications
            // would arrive and be rejected. The step is reported as failed so it is not mistaken for done.
            report.add(STEP_HMAC, false, "The webhook was created but Adyen returned no HMAC key. "
                    + "Notifications cannot be verified until one is generated.");
            return false;
        }

        store.setAdyenNotificationHMACKey(hmacKey);
        modelService.save(store);
        report.add(STEP_HMAC, true, "Generated the webhook HMAC key.");
        return true;
    }

    /**
     * One Management API call. Answers {@code null} on any refusal rather than throwing, because the caller
     * records a step either way and must not lose what earlier steps already produced.
     */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> post(final String path, final Map<String, Object> body,
            final String managementKey) {
        final HttpHeaders headers = new HttpHeaders();
        headers.set(X_API_KEY, managementKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            return restTemplate.exchange(endpoint() + path, HttpMethod.POST,
                    new HttpEntity<>(body, headers), Map.class).getBody();
        } catch (final IllegalArgumentException e) {
            LOG.error("The configured Management API endpoint is not a usable absolute URL.");
            return null;
        } catch (final RestClientException e) {
            // The path and the exception type only. Adyen's body can echo the request, and the request
            // carries credentials.
            LOG.warn("Adyen refused POST {} ({}).", path, e.getClass().getSimpleName());
            return null;
        }
    }

    protected String newPassword() {
        final byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    protected String endpoint() {
        return StringUtils.defaultIfBlank(
                configurationService.getConfiguration().getString(ENDPOINT_PROPERTY), DEFAULT_ENDPOINT);
    }

    public void setConfigurationService(final ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    public void setModelService(final ModelService modelService) {
        this.modelService = modelService;
    }

    public void setAdyenSetupService(final AdyenSetupService adyenSetupService) {
        this.adyenSetupService = adyenSetupService;
    }
}
