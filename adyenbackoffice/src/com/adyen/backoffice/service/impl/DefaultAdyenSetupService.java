package com.adyen.backoffice.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.adyen.backoffice.dto.AdyenCredentialCheckWsDTO;
import com.adyen.backoffice.dto.MeApiCredentialWsDTO;
import com.adyen.backoffice.service.AdyenSetupService;

import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;

/**
 * Default implementation. Talks to Adyen only through {@code GET /me}, which is the one Management API
 * call a credential can always make about itself.
 */
public class DefaultAdyenSetupService implements AdyenSetupService {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultAdyenSetupService.class);

    /** Where a key lived before the wizard existed. Still read, never written. */
    private static final String LEGACY_KEY_PROPERTY = "adyen.management.api.key";
    private static final String ENDPOINT_PROPERTY = "adyen.management.api.endpoint";
    /** Used when the installation has not set the property, so a fresh one reaches Adyen at all. */
    private static final String DEFAULT_ENDPOINT = "https://management-test.adyen.com/v3";
    private static final String X_API_KEY = "X-API-Key";

    /**
     * What the wizard cannot do without. Creating the store's Checkout credential needs the first; the
     * webhook and its HMAC key need the second. A key without them authenticates fine and then fails
     * halfway through, which is why they are checked up front.
     */
    private static final List<String> REQUIRED_ROLES = List.of(
            "Management API—API credentials read and write",
            "Management API—Webhooks read and write");

    /**
     * What each required role reduces to once case, dash characters and spacing are normalised away.
     * Adyen renders these names with a dash whose character and surrounding spaces differ between
     * Customer Area versions, and comparing the raw strings rejects credentials that hold the role.
     */
    private static final List<String> REQUIRED_ROLE_MARKERS = List.of(
            "api credentials read and write",
            "webhooks read and write");

    private ConfigurationService configurationService;
    private ModelService modelService;
    private BaseStoreService baseStoreService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String apiKeyFor(final BaseStoreModel store) {
        final String onStore = store == null ? null : store.getAdyenManagementApiKey();
        if (StringUtils.isNotBlank(onStore)) {
            return onStore;
        }
        return StringUtils.trimToNull(
                configurationService.getConfiguration().getString(LEGACY_KEY_PROPERTY));
    }

    @Override
    public String managementApiKey() {
        for (final BaseStoreModel store : baseStoreService.getAllBaseStores()) {
            final String key = store.getAdyenManagementApiKey();
            if (StringUtils.isNotBlank(key)) {
                return key;
            }
        }
        return StringUtils.trimToNull(
                configurationService.getConfiguration().getString(LEGACY_KEY_PROPERTY));
    }

    @Override
    public boolean isConfigured(final BaseStoreModel store) {
        return StringUtils.isNotBlank(apiKeyFor(store));
    }

    @Override
    public AdyenCredentialCheckWsDTO check(final String apiKey) {
        final AdyenCredentialCheckWsDTO result = new AdyenCredentialCheckWsDTO();
        result.setRoles(List.of());
        // Stays empty until Adyen describes the credential. Naming the required roles on a key Adyen
        // never recognised reports a missing permission, which is a different fault from a rejected key.
        result.setMissingRoles(List.of());
        if (StringUtils.isBlank(apiKey)) {
            return result;
        }

        final MeApiCredentialWsDTO me = fetchMe(apiKey);
        if (me == null) {
            return result;
        }

        final List<String> roles = me.getRoles() == null ? List.of() : me.getRoles();
        final List<String> missing = new ArrayList<>();
        for (int i = 0; i < REQUIRED_ROLES.size(); i++) {
            if (!holdsRole(roles, REQUIRED_ROLE_MARKERS.get(i))) {
                missing.add(REQUIRED_ROLES.get(i));
            }
        }
        if (!missing.isEmpty()) {
            // Role names are not credentials, and seeing what Adyen actually returned is the only way to
            // tell a genuinely missing permission from a name this code failed to recognise.
            LOG.info("Adyen credential '{}' reports roles {}, which do not cover {}.",
                    me.getUsername(), roles, missing);
        }

        result.setActive(me.isActive());
        result.setCompanyName(me.getCompanyName());
        result.setUsername(me.getUsername());
        result.setRoles(roles);
        result.setMissingRoles(missing);
        result.setUsable(me.isActive() && missing.isEmpty());
        return result;
    }

    @Override
    public AdyenCredentialCheckWsDTO saveIfUsable(final BaseStoreModel store, final String apiKey) {
        final AdyenCredentialCheckWsDTO check = check(apiKey);
        if (!check.isUsable() || store == null) {
            return check;
        }
        store.setAdyenManagementApiKey(apiKey);
        modelService.save(store);
        // The key is never logged; the company it administers is what makes the line useful.
        LOG.info("Stored a Management API key for base store '{}' (Adyen company '{}').",
                store.getUid(), check.getCompanyName());
        return check;
    }

    private static boolean holdsRole(final List<String> roles, final String marker) {
        for (final String role : roles) {
            if (normalise(role).contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static String normalise(final String role) {
        return role == null ? ""
                : role.toLowerCase(Locale.ROOT).replaceAll("\\p{Pd}", " ").replaceAll("\\s+", " ").trim();
    }

    /**
     * Ask Adyen to describe the credential it was called with. Separated so a test can exercise the role
     * checking without reaching the network.
     *
     * @return what Adyen said, or {@code null} when it refused the credential
     */
    protected MeApiCredentialWsDTO fetchMe(final String apiKey) {
        final HttpHeaders headers = new HttpHeaders();
        headers.set(X_API_KEY, apiKey);
        try {
            final ResponseEntity<MeApiCredentialWsDTO> response = restTemplate.exchange(
                    endpoint() + "/me", HttpMethod.GET, new HttpEntity<>(headers),
                    MeApiCredentialWsDTO.class);
            return response.getBody();
        } catch (final IllegalArgumentException e) {
            // A misconfigured endpoint reaches RestTemplate as a relative URI. Reported as unusable rather
            // than thrown, so a configuration mistake does not surface as a server error.
            LOG.error("The configured Management API endpoint is not a usable absolute URL.");
            return null;
        } catch (final RestClientException e) {
            // Never the key itself, and never the vendor's message: a rejected credential says only that
            // it was rejected, and Adyen's body can echo what was submitted.
            LOG.info("Adyen refused the submitted Management API key ({}).", e.getClass().getSimpleName());
            return null;
        }
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

    public void setBaseStoreService(final BaseStoreService baseStoreService) {
        this.baseStoreService = baseStoreService;
    }
}
