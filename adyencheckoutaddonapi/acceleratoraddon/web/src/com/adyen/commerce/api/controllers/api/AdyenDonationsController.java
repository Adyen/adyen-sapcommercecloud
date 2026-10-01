package com.adyen.commerce.api.controllers.api;

import com.adyen.commerce.facades.AdyenCheckoutFacade;
import com.adyen.commerce.facades.AdyenDonationsFacade;
import com.adyen.commerce.facades.impl.DefaultAdyenCheckoutFacade;
import com.adyen.commerce.api.request.DonationRequest;
import com.adyen.commerce.api.response.DonationResponse;
import com.adyen.model.checkout.Amount;
import com.adyen.model.checkout.CardDonations;
import com.adyen.model.checkout.DonationCampaign;
import com.adyen.model.checkout.DonationCampaignsResponse;
import com.adyen.model.checkout.DonationPaymentMethod;
import com.adyen.model.checkout.DonationPaymentRequest;
import com.adyen.model.checkout.DonationPaymentResponse;
import com.adyen.service.exception.ApiException;
import de.hybris.platform.acceleratorservices.urlresolver.SiteBaseUrlResolutionService;
import de.hybris.platform.basecommerce.model.site.BaseSiteModel;
import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;
import de.hybris.platform.site.BaseSiteService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/api/checkout/donations")
public class AdyenDonationsController {
    private static final Logger LOG = Logger.getLogger(AdyenDonationsController.class);

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
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getContext() throws Exception {
        final String donationToken = sessionService.getAttribute(DefaultAdyenCheckoutFacade.SESSION_DONATION_TOKEN);
        final String originalPspReference = sessionService.getAttribute(DefaultAdyenCheckoutFacade.SESSION_DONATION_ORIGINAL_PSP_REFERENCE);
        final Long commercialTxAmount = sessionService.getAttribute(DefaultAdyenCheckoutFacade.SESSION_DONATION_ORIGINAL_AMOUNT_VALUE);
        final String currency = sessionService.getAttribute(DefaultAdyenCheckoutFacade.SESSION_DONATION_ORIGINAL_AMOUNT_CURRENCY);
        final String countryCode = sessionService.getAttribute(DefaultAdyenCheckoutFacade.SESSION_DONATION_COUNTRY_CODE);

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

        final DonationCampaignsResponse campaignsResponse = adyenDonationsFacade.getDonationCampaigns();
        final List<DonationCampaign> campaigns = campaignsResponse.getDonationCampaigns() == null
                ? Collections.emptyList() : campaignsResponse.getDonationCampaigns();
        if (campaigns.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        final Map<String, Object> context = new LinkedHashMap<>();
        context.put("clientKey", baseStore.getAdyenClientKey());
        context.put("environment", adyenCheckoutFacade.getEnvironmentMode());
        context.put("locale", adyenCheckoutFacade.getShopperLocale());
        context.put("commercialTxAmount", commercialTxAmount);
        context.put("currency", currency);
        context.put("countryCode", countryCode);
        context.put("campaigns", campaigns);
        return ResponseEntity.ok(context);
    }

    @PostMapping(value = "/donate", consumes = "application/json", produces = "application/json")
    @ResponseBody
    public ResponseEntity<DonationResponse> donate(@Valid @RequestBody final DonationRequest request) throws Exception {
        final String donationToken = sessionService.getAttribute(DefaultAdyenCheckoutFacade.SESSION_DONATION_TOKEN);
        final String originalPspReference = sessionService.getAttribute(DefaultAdyenCheckoutFacade.SESSION_DONATION_ORIGINAL_PSP_REFERENCE);
        final BaseStoreModel baseStore = baseStoreService.getCurrentBaseStore();

        if (request == null || request.getAmount() == null || request.getAmount().getValue() == null
                || request.getAmount().getValue() <= 0 || StringUtils.isAnyBlank(request.getDonationCampaignId(),
                request.getAmount().getCurrency(), donationToken, originalPspReference)
                || baseStore == null || StringUtils.isBlank(baseStore.getAdyenMerchantAccount())) {
            return ResponseEntity.badRequest().build();
        }

        final DonationPaymentRequest donationRequest = new DonationPaymentRequest();
        donationRequest.setAmount(new Amount().currency(request.getAmount().getCurrency()).value(request.getAmount().getValue()));
        donationRequest.setDonationCampaignId(request.getDonationCampaignId());
        donationRequest.setDonationToken(donationToken);
        donationRequest.setDonationOriginalPspReference(originalPspReference);
        donationRequest.setMerchantAccount(baseStore.getAdyenMerchantAccount());
        donationRequest.setReference(originalPspReference + "-donation-" + System.currentTimeMillis());
        donationRequest.setPaymentMethod(new DonationPaymentMethod(
                new CardDonations().type(CardDonations.TypeEnum.SCHEME)));
        donationRequest.setReturnUrl(getDonationReturnUrl());

        try {
            final DonationPaymentResponse response = adyenDonationsFacade.makeDonationPayment(donationRequest);
            return ResponseEntity.ok(DonationResponse.from(response));
        } catch (ApiException exception) {
            LOG.warn("Adyen donation request failed", exception);
            return ResponseEntity.status(exception.getStatusCode()).build();
        }
    }

    protected String getDonationReturnUrl() {
        final BaseSiteModel currentBaseSite = baseSiteService.getCurrentBaseSite();
        return siteBaseUrlResolutionService.getWebsiteUrlForSite(currentBaseSite, true, "/");
    }

}
