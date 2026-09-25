package com.adyen.backoffice.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.apache.commons.configuration2.Configuration;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adyen.backoffice.dto.AdyenCredentialCheckWsDTO;
import com.adyen.backoffice.dto.MeApiCredentialWsDTO;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;

@UnitTest
public class DefaultAdyenSetupServiceTest {

    private static final String CREDENTIALS_ROLE = "Management API—API credentials read and write";
    private static final String WEBHOOKS_ROLE = "Management API—Webhooks read and write";

    @Mock
    private ConfigurationService configurationService;
    @Mock
    private Configuration configuration;
    @Mock
    private ModelService modelService;
    @Mock
    private BaseStoreService baseStoreService;

    private MeApiCredentialWsDTO adyenAnswer;
    private DefaultAdyenSetupService service;
    private BaseStoreModel store;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(configurationService.getConfiguration()).thenReturn(configuration);
        adyenAnswer = null;

        service = new DefaultAdyenSetupService() {
            @Override
            protected MeApiCredentialWsDTO fetchMe(final String apiKey) {
                return adyenAnswer;
            }
        };
        service.setConfigurationService(configurationService);
        service.setModelService(modelService);
        service.setBaseStoreService(baseStoreService);
        store = mock(BaseStoreModel.class);
    }

    /** The browsing screens are not store-scoped, so they take the first key an installation holds. */
    @Test
    public void browsesAdyenWithTheFirstStoredKey() {
        final BaseStoreModel without = mock(BaseStoreModel.class);
        when(without.getAdyenManagementApiKey()).thenReturn(null);
        when(store.getAdyenManagementApiKey()).thenReturn("on-store");
        when(baseStoreService.getAllBaseStores()).thenReturn(List.of(without, store));

        assertEquals("on-store", service.managementApiKey());
    }

    @Test
    public void fallsBackToTheLegacyPropertyWhenNoStoreHoldsAKey() {
        when(baseStoreService.getAllBaseStores()).thenReturn(List.of(store));
        when(store.getAdyenManagementApiKey()).thenReturn(null);
        when(configuration.getString("adyen.management.api.key")).thenReturn("legacy");

        assertEquals("legacy", service.managementApiKey());
    }

    /** The store's own key wins; the global property is only what an older installation left behind. */
    @Test
    public void prefersTheStoresOwnKeyOverTheLegacyGlobalProperty() {
        when(store.getAdyenManagementApiKey()).thenReturn("on-store");
        when(configuration.getString("adyen.management.api.key")).thenReturn("legacy");

        assertEquals("on-store", service.apiKeyFor(store));
    }

    /** An installation configured before the wizard existed must keep working. */
    @Test
    public void fallsBackToTheLegacyPropertyWhenTheStoreHasNoKey() {
        when(store.getAdyenManagementApiKey()).thenReturn(null);
        when(configuration.getString("adyen.management.api.key")).thenReturn("legacy");

        assertEquals("legacy", service.apiKeyFor(store));
        assertTrue(service.isConfigured(store));
    }

    @Test
    public void reportsAKeyCarryingEveryRequiredRoleAsUsable() {
        adyenAnswer = credential(true, List.of(CREDENTIALS_ROLE, WEBHOOKS_ROLE, "Management API—Stores read"));

        final AdyenCredentialCheckWsDTO check = service.check("key");

        assertTrue(check.isUsable());
        assertEquals("TestCompany", check.getCompanyName());
        assertTrue(check.getMissingRoles().isEmpty());
    }

    /**
     * The whole point of checking up front: a key that authenticates but cannot create credentials would
     * otherwise fail several steps later, in a step that has nothing to do with it.
     */
    @Test
    public void namesTheRoleThatIsMissingRatherThanJustRefusing() {
        adyenAnswer = credential(true, List.of(WEBHOOKS_ROLE));

        final AdyenCredentialCheckWsDTO check = service.check("key");

        assertFalse(check.isUsable());
        assertEquals(List.of(CREDENTIALS_ROLE), check.getMissingRoles());
    }

    /** A credential Adyen has switched off authenticates but can do nothing. */
    @Test
    public void refusesAnInactiveCredentialEvenWithEveryRole() {
        adyenAnswer = credential(false, List.of(CREDENTIALS_ROLE, WEBHOOKS_ROLE));

        assertFalse(service.check("key").isUsable());
    }

    /**
     * Verbatim from a company-level credential. Adyen mixes dash characters within one response, so the
     * role names are kept exactly as returned rather than tidied to one convention.
     */
    private static final List<String> ROLES_AS_ADYEN_RETURNS_THEM = List.of(
            "Management API - Accounts read",
            "Management API - Webhooks read",
            "Cloud Device API role",
            "Management API - API credentials read and write",
            "Management API - Stores read",
            "Management API \u2014 Payment methods read",
            "Allow SDK download for POS developers",
            "API Clientside Encryption Payments role",
            "Management API - Stores read and write",
            "Management API - Webhooks read and write",
            "Checkout encrypted cardholder data",
            "Merchant Recurring role",
            "Data Protection API",
            "Management API - Payout Account Settings Read",
            "Checkout webservice role",
            "Adyen Payments App role",
            "Management API - Accounts read and write",
            "Merchant PAL Webservice role");

    @Test
    public void acceptsTheRoleNamesAdyenActuallyReturns() {
        adyenAnswer = credential(true, ROLES_AS_ADYEN_RETURNS_THEM);

        assertTrue(service.check("key").isUsable());
    }

    @Test
    public void acceptsRoleNamesWhateverDashAndSpacingAdyenUses() {
        adyenAnswer = credential(true, List.of(
                "Management API - API credentials read and write",
                "Management API \u2014 Webhooks read and write"));

        assertTrue(service.check("key").isUsable());
    }

    @Test
    public void stillRefusesARoleThatOnlyReads() {
        adyenAnswer = credential(true, List.of(
                "Management API - API credentials read",
                "Management API - Webhooks read and write"));

        assertFalse(service.check("key").isUsable());
    }

    @Test
    public void refusesAKeyAdyenWouldNotDescribe() {
        adyenAnswer = null;

        final AdyenCredentialCheckWsDTO check = service.check("bad-key");

        assertFalse(check.isUsable());
        // Adyen never described it, so no role can be reported as missing.
        assertTrue(check.getMissingRoles().isEmpty());
    }

    @Test
    public void refusesABlankKeyWithoutAskingAdyen() {
        assertFalse(service.check("  ").isUsable());
        assertFalse(service.check(null).isUsable());
    }

    /** Validation precedes persistence: an unusable key must leave the store untouched. */
    @Test
    public void storesNothingWhenTheKeyIsNotUsable() {
        adyenAnswer = credential(true, List.of(WEBHOOKS_ROLE));

        assertFalse(service.saveIfUsable(store, "key").isUsable());

        verify(store, never()).setAdyenManagementApiKey(any());
        verify(modelService, never()).save(any());
    }

    @Test
    public void storesTheKeyOnceAdyenConfirmsIt() {
        adyenAnswer = credential(true, List.of(CREDENTIALS_ROLE, WEBHOOKS_ROLE));

        assertTrue(service.saveIfUsable(store, "good-key").isUsable());

        verify(store).setAdyenManagementApiKey("good-key");
        verify(modelService).save(store);
    }

    private MeApiCredentialWsDTO credential(final boolean active, final List<String> roles) {
        final MeApiCredentialWsDTO me = new MeApiCredentialWsDTO();
        me.setActive(active);
        me.setCompanyName("TestCompany");
        me.setUsername("ws@Company.TestCompany");
        me.setRoles(roles);
        return me;
    }
}
