package com.adyen.commerce.api.controllers.api;

import com.adyen.commerce.api.request.DonationRequest;
import com.adyen.commerce.api.response.DonationContextResponse;
import com.adyen.commerce.api.response.DonationResponse;
import com.adyen.commerce.data.DonationPaymentData;
import com.adyen.commerce.facades.AdyenCheckoutFacade;
import com.adyen.commerce.facades.AdyenDonationsFacade;
import com.adyen.commerce.facades.impl.DefaultAdyenCheckoutFacade;
import com.adyen.commerce.services.DonationCampaignValidator;
import com.adyen.model.checkout.DonationCampaign;
import com.adyen.model.checkout.DonationCampaignsResponse;
import com.adyen.model.checkout.DonationPaymentResponse;
import com.adyen.service.exception.ApiException;
import com.google.gson.Gson;
import de.hybris.platform.acceleratorservices.urlresolver.SiteBaseUrlResolutionService;
import de.hybris.platform.basecommerce.model.site.BaseSiteModel;
import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;
import de.hybris.platform.site.BaseSiteService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/checkout/donations")
public class AdyenDonationsController {
    private static final Logger LOG = Logger.getLogger(AdyenDonationsController.class);
    private static final String COMPLETED = "completed";
    private static final String SESSION_DONATION_CAMPAIGNS = "adyen_donation_campaigns";
    private static final String SESSION_DONATION_CAMPAIGNS_CURRENCY = "adyen_donation_campaigns_currency";
    private static final Gson GSON = new Gson();

    private static final DonationCampaignValidator DONATION_CAMPAIGN_VALIDATOR = new DonationCampaignValidator();

    @Resource(name = "adyenDonationsFacade")
    private AdyenDonationsFacade adyenDonationsFacade;

    @Resource(name = "adyenCheckoutFacade")
    private AdyenCheckoutFacade adyenCheckoutFacade;

    @Resource(name = "sessionService")
    private SessionService sessionService;

    @Resource(name = "baseStoreService")
    private BaseStoreService baseStoreService;

    @Resource(name = "baseSiteService")
    private BaseSiteService baseSiteService;

    @Resource(name = "siteBaseUrlResolutionService")
    private SiteBaseUrlResolutionService siteBaseUrlResolutionService;

    @GetMapping(value = "/context", produces = "application/json")
    public ResponseEntity<DonationContextResponse> getContext() {
        final String donationToken = sessionService.getAttribute(DefaultAdyenCheckoutFacade.SESSION_DONATION_TOKEN);
        final String originalPspReference = sessionService.getAttribute(DefaultAdyenCheckoutFacade.SESSION_DONATION_ORIGINAL_PSP_REFERENCE);
        final Long commercialTxAmount = sessionService.getAttribute(DefaultAdyenCheckoutFacade.SESSION_DONATION_ORIGINAL_AMOUNT_VALUE);
        final String currency = sessionService.getAttribute(DefaultAdyenCheckoutFacade.SESSION_DONATION_ORIGINAL_AMOUNT_CURRENCY);
        final String countryCode = sessionService.getAttribute(DefaultAdyenCheckoutFacade.SESSION_DONATION_COUNTRY_CODE);

        clearDonationCampaignCache();

        if (StringUtils.isAnyBlank(donationToken, originalPspReference, currency, countryCode) || commercialTxAmount == null) {
            LOG.info("Giving context unavailable: token=" + StringUtils.isNotBlank(donationToken)
                    + ", pspReference=" + StringUtils.isNotBlank(originalPspReference)
                    + ", amount=" + (commercialTxAmount != null)
                    + ", currency=" + StringUtils.isNotBlank(currency)
                    + ", countryCode=" + StringUtils.isNotBlank(countryCode));
            return ResponseEntity.noContent().build();
        }

        final BaseStoreModel baseStore = baseStoreService.getCurrentBaseStore();
        if (baseStore == null || StringUtils.isBlank(baseStore.getAdyenClientKey())) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        final List<DonationCampaign> campaigns;
        try {
            campaigns = getCampaigns(currency);
        } catch (ApiException | IOException exception) {
            LOG.warn("Unable to load Adyen Giving campaigns", exception);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
        if (campaigns.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        cacheDonationCampaigns(currency, campaigns);

        return ResponseEntity.ok(new DonationContextResponse(baseStore.getAdyenClientKey(),
                adyenCheckoutFacade.getEnvironmentMode(), adyenCheckoutFacade.getShopperLocale(),
                commercialTxAmount, currency, countryCode, campaigns));
    }

    @PostMapping(value = "/donate", consumes = "application/json", produces = "application/json")
    public ResponseEntity<DonationResponse> donate(@Valid @RequestBody final DonationRequest request,
                                                     final HttpServletRequest httpServletRequest) {
        final String donationToken = sessionService.getAttribute(DefaultAdyenCheckoutFacade.SESSION_DONATION_TOKEN);
        final String originalPspReference = sessionService.getAttribute(DefaultAdyenCheckoutFacade.SESSION_DONATION_ORIGINAL_PSP_REFERENCE);
        final Long commercialTransactionValue = sessionService.getAttribute(DefaultAdyenCheckoutFacade.SESSION_DONATION_ORIGINAL_AMOUNT_VALUE);
        final BaseStoreModel baseStore = baseStoreService.getCurrentBaseStore();

        if (StringUtils.isAnyBlank(donationToken, originalPspReference)
                || baseStore == null || StringUtils.isBlank(baseStore.getAdyenMerchantAccount())) {
            return ResponseEntity.badRequest().build();
        }

        final DonationCampaign campaign;
        try {
            campaign = findCampaign(request.getDonationCampaignId(), getCampaignsForDonation(request.getAmount().getCurrency()));
        } catch (ApiException | IOException exception) {
            LOG.warn("Unable to validate Adyen Giving campaign", exception);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
        if (!DONATION_CAMPAIGN_VALIDATOR.isValid(campaign, request.getAmount().getCurrency(),
                request.getAmount().getValue(), commercialTransactionValue)) {
            LOG.warn("Rejected invalid Adyen Giving campaign or amount");
            return ResponseEntity.badRequest().build();
        }

        final HttpSession httpSession = httpServletRequest.getSession(false);
        if (httpSession == null || !beginDonation(httpSession)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        final DonationPaymentData paymentData = createDonationPaymentData(request, donationToken, originalPspReference);

        boolean completed = false;
        try {
            final DonationPaymentResponse response = adyenDonationsFacade.makeDonationPayment(paymentData,
                    getOrCreateDonationIdempotencyKey());
            final DonationResponse donationResponse = DonationResponse.from(response);
            completed = COMPLETED.equalsIgnoreCase(donationResponse.getStatus());
            if (completed) {
                clearDonationContext(httpSession);
            }
            return ResponseEntity.ok(donationResponse);
        } catch (ApiException exception) {
            LOG.warn("Adyen donation request failed", exception);
            return ResponseEntity.status(exception.getStatusCode()).build();
        } catch (IOException exception) {
            LOG.warn("Adyen donation request could not reach Adyen", exception);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        } finally {
            if (!completed) {
                endDonation(httpSession);
            }
        }
    }

    protected List<DonationCampaign> getCampaigns(final String currency) throws IOException, ApiException {
        final DonationCampaignsResponse campaignsResponse = adyenDonationsFacade.getDonationCampaigns(currency);
        return campaignsResponse == null || campaignsResponse.getDonationCampaigns() == null
                ? Collections.emptyList() : campaignsResponse.getDonationCampaigns();
    }

    protected DonationCampaign findCampaign(final String campaignId, final List<DonationCampaign> campaigns) {
        return campaigns.stream()
                .filter(campaign -> StringUtils.equals(campaignId, campaign.getId()))
                .findFirst()
                .orElse(null);
    }

    protected boolean beginDonation(final HttpSession httpSession) {
        synchronized (httpSession) {
            if (Boolean.TRUE.equals(sessionService.getAttribute(DefaultAdyenCheckoutFacade.SESSION_DONATION_IN_PROGRESS))) {
                return false;
            }
            sessionService.setAttribute(DefaultAdyenCheckoutFacade.SESSION_DONATION_IN_PROGRESS, Boolean.TRUE);
            return true;
        }
    }

    protected void endDonation(final HttpSession httpSession) {
        synchronized (httpSession) {
            sessionService.removeAttribute(DefaultAdyenCheckoutFacade.SESSION_DONATION_IN_PROGRESS);
        }
    }

    protected void clearDonationContext(final HttpSession httpSession) {
        synchronized (httpSession) {
            sessionService.removeAttribute(DefaultAdyenCheckoutFacade.SESSION_DONATION_TOKEN);
            sessionService.removeAttribute(DefaultAdyenCheckoutFacade.SESSION_DONATION_ORIGINAL_PSP_REFERENCE);
            sessionService.removeAttribute(DefaultAdyenCheckoutFacade.SESSION_DONATION_ORIGINAL_AMOUNT_VALUE);
            sessionService.removeAttribute(DefaultAdyenCheckoutFacade.SESSION_DONATION_ORIGINAL_AMOUNT_CURRENCY);
            sessionService.removeAttribute(DefaultAdyenCheckoutFacade.SESSION_DONATION_COUNTRY_CODE);
            sessionService.removeAttribute(DefaultAdyenCheckoutFacade.SESSION_DONATION_IN_PROGRESS);
            sessionService.removeAttribute(DefaultAdyenCheckoutFacade.SESSION_DONATION_IDEMPOTENCY_KEY);
            sessionService.removeAttribute(DefaultAdyenCheckoutFacade.SESSION_DONATION_REFERENCE);
            clearDonationCampaignCache();
        }
    }

    protected List<DonationCampaign> getCampaignsForDonation(final String currency) throws IOException, ApiException {
        final String cachedCurrency = sessionService.getAttribute(SESSION_DONATION_CAMPAIGNS_CURRENCY);
        final String cachedCampaigns = sessionService.getAttribute(SESSION_DONATION_CAMPAIGNS);
        if (StringUtils.equalsIgnoreCase(currency, cachedCurrency)
                && StringUtils.isNotBlank(cachedCampaigns)) {
            final DonationCampaign[] cachedCampaignArray = GSON.fromJson(cachedCampaigns, DonationCampaign[].class);
            final List<DonationCampaign> campaigns = cachedCampaignArray == null
                    ? null : Arrays.asList(cachedCampaignArray);
            if (campaigns != null) {
                return campaigns;
            }
        }

        final List<DonationCampaign> campaigns = getCampaigns(currency);
        cacheDonationCampaigns(currency, campaigns);
        return campaigns;
    }

    protected void cacheDonationCampaigns(final String currency, final List<DonationCampaign> campaigns) {
        sessionService.setAttribute(SESSION_DONATION_CAMPAIGNS, GSON.toJson(campaigns));
        sessionService.setAttribute(SESSION_DONATION_CAMPAIGNS_CURRENCY, currency);
    }

    protected void clearDonationCampaignCache() {
        sessionService.removeAttribute(SESSION_DONATION_CAMPAIGNS);
        sessionService.removeAttribute(SESSION_DONATION_CAMPAIGNS_CURRENCY);
    }

    protected String getOrCreateDonationIdempotencyKey() {
        String idempotencyKey = sessionService.getAttribute(DefaultAdyenCheckoutFacade.SESSION_DONATION_IDEMPOTENCY_KEY);
        if (StringUtils.isBlank(idempotencyKey)) {
            idempotencyKey = UUID.randomUUID().toString();
            sessionService.setAttribute(DefaultAdyenCheckoutFacade.SESSION_DONATION_IDEMPOTENCY_KEY, idempotencyKey);
        }
        return idempotencyKey;
    }

    protected String getOrCreateDonationReference(final String originalPspReference) {
        String reference = sessionService.getAttribute(DefaultAdyenCheckoutFacade.SESSION_DONATION_REFERENCE);
        if (StringUtils.isBlank(reference)) {
            reference = originalPspReference + "-donation-" + System.currentTimeMillis();
            sessionService.setAttribute(DefaultAdyenCheckoutFacade.SESSION_DONATION_REFERENCE, reference);
        }
        return reference;
    }

    protected String getDonationReturnUrl() {
        final BaseSiteModel currentBaseSite = baseSiteService.getCurrentBaseSite();
        return siteBaseUrlResolutionService.getWebsiteUrlForSite(currentBaseSite, true, "/");
    }

    protected DonationPaymentData createDonationPaymentData(final DonationRequest request, final String donationToken,
                                                             final String originalPspReference) {
        final DonationPaymentData paymentData = new DonationPaymentData();
        paymentData.setAmountValue(request.getAmount().getValue());
        paymentData.setAmountCurrency(request.getAmount().getCurrency());
        paymentData.setCampaignId(request.getDonationCampaignId());
        paymentData.setDonationToken(donationToken);
        paymentData.setOriginalPspReference(originalPspReference);
        paymentData.setReference(getOrCreateDonationReference(originalPspReference));
        paymentData.setReturnUrl(getDonationReturnUrl());
        return paymentData;
    }

}
