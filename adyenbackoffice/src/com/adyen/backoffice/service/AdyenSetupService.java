package com.adyen.backoffice.service;

import com.adyen.backoffice.dto.AdyenCredentialCheckWsDTO;

import de.hybris.platform.store.BaseStoreModel;

/**
 * The setup wizard's view of a store's Adyen configuration: whether it has a Management API key, what a
 * candidate key actually is, and storing one once it is known to work.
 *
 * <p>Kept apart from {@link AdyenManagementService}, which assumes a working credential and reads Adyen
 * with it. This is what runs before that assumption holds.</p>
 */
public interface AdyenSetupService {

    /**
     * The Management API key this store configures Adyen with, or {@code null} when it has none.
     *
     * <p>Read from the store rather than from platform configuration, because the merchant account the key
     * administers is itself a property of the store. A key left in the legacy global property is still
     * honoured so that an installation configured before the wizard existed keeps working.</p>
     */
    String apiKeyFor(BaseStoreModel store);

    boolean isConfigured(BaseStoreModel store);

    /**
     * Ask Adyen what this key is, without storing it.
     *
     * <p>{@code GET /me} answers with the credential's own description, so the wizard can name the company
     * the merchant is about to configure and list the roles the key still needs.</p>
     */
    AdyenCredentialCheckWsDTO check(String apiKey);

    /**
     * Store the key against this store, but only if Adyen confirms it is usable.
     *
     * <p>Validation precedes persistence on purpose: a key accepted here and found wrong later surfaces as
     * a failure in a step that has nothing to do with it.</p>
     *
     * @return what the check found; nothing is stored unless {@code usable} is true
     */
    AdyenCredentialCheckWsDTO saveIfUsable(BaseStoreModel store, String apiKey);

    /**
     * The key the merchant-browsing screens read Adyen with.
     *
     * <p>Those screens show one Adyen company rather than one store, so they cannot name a store to take
     * the key from; the first store that has one supplies it. An installation whose stores belong to
     * different Adyen companies would need that choice surfaced in the UI.</p>
     *
     * @return a stored key, the legacy global property, or {@code null} when nothing is configured
     */
    String managementApiKey();
}
