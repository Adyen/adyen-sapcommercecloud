package com.adyen.backoffice.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.adyen.backoffice.dto.AdyenCredentialCheckWsDTO;
import com.adyen.backoffice.dto.ProvisionReportWsDTO;
import com.adyen.backoffice.dto.ProvisionRequestWsDTO;
import com.adyen.backoffice.dto.SetupStatusWsDTO;
import com.adyen.backoffice.dto.StoreSetupWsDTO;
import com.adyen.backoffice.dto.SubmitApiKeyWsDTO;
import com.adyen.backoffice.service.AdyenProvisioningService;
import com.adyen.backoffice.service.AdyenSetupService;

import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;

import jakarta.validation.Valid;

/**
 * The setup wizard: what is still unconfigured, and taking the Management API key that fixes it.
 *
 * <p>Under {@code /api/setup}, not {@code /api/auth}, so it stays behind the extension's own Spring
 * Security - only a signed-in SAP Commerce administrator reaches it. Adyen never authenticates anybody
 * here; the key is a credential the merchant already created in their Customer Area.</p>
 */
@Controller
@RequestMapping("/api/setup")
public class SetupController {

    @Autowired
    private AdyenSetupService adyenSetupService;

    @Autowired
    private AdyenProvisioningService adyenProvisioningService;

    @Autowired
    private BaseStoreService baseStoreService;

    /** Which stores still need a key. The wizard shows itself when any of them does. */
    @GetMapping("/status")
    public ResponseEntity<SetupStatusWsDTO> status() {
        final List<StoreSetupWsDTO> stores = new ArrayList<>();
        boolean anyUnconfigured = false;
        for (final BaseStoreModel store : baseStoreService.getAllBaseStores()) {
            final StoreSetupWsDTO entry = new StoreSetupWsDTO();
            entry.setUid(store.getUid());
            entry.setName(store.getName());
            entry.setMerchantAccount(store.getAdyenMerchantAccount());
            entry.setConfigured(adyenSetupService.isConfigured(store));
            anyUnconfigured |= !entry.isConfigured();
            stores.add(entry);
        }
        final SetupStatusWsDTO status = new SetupStatusWsDTO();
        status.setStores(stores);
        status.setSetupRequired(anyUnconfigured);
        return ResponseEntity.ok(status);
    }

    /**
     * Check a key and, if Adyen says it is usable, keep it against the store.
     *
     * <p>Answers with what the key turned out to be either way, so a key that authenticates but lacks a
     * role is refused with the role named rather than accepted and blamed on a later step.</p>
     */
    @PostMapping("/api-key")
    public ResponseEntity<AdyenCredentialCheckWsDTO> submitApiKey(
            @Valid @RequestBody final SubmitApiKeyWsDTO request) {
        final BaseStoreModel store = baseStoreService.getBaseStoreForUid(request.getBaseStore());
        if (store == null) {
            return ResponseEntity.badRequest().build();
        }
        final AdyenCredentialCheckWsDTO check =
                adyenSetupService.saveIfUsable(store, request.getApiKey());
        // 422, not 400: the request was well formed and the refusal came from Adyen, not from the shape
        // of what was sent.
        return check.isUsable() ? ResponseEntity.ok(check)
                : ResponseEntity.unprocessableEntity().body(check);
    }

    /**
     * Configure this store's Adyen account: create the Checkout credential, register the storefront
     * origin, create the webhook and generate its HMAC key.
     *
     * <p>Answers 200 only when every step succeeded. A partial run is 422 with the steps that did and did
     * not happen, because what Adyen created cannot be undone from here.</p>
     */
    @PostMapping("/provision")
    public ResponseEntity<ProvisionReportWsDTO> provision(
            @Valid @RequestBody final ProvisionRequestWsDTO request) {
        final BaseStoreModel store = baseStoreService.getBaseStoreForUid(request.getBaseStore());
        if (store == null) {
            return ResponseEntity.badRequest().build();
        }
        final ProvisionReportWsDTO report = adyenProvisioningService.provision(store, request);
        return report.isComplete() ? ResponseEntity.ok(report)
                : ResponseEntity.unprocessableEntity().body(report);
    }
}
