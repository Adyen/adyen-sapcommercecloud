package com.adyen.commerce.api.controllers.api;

import com.adyen.commerce.facades.AdyenCheckoutFacade;
import com.adyen.commerce.facades.AdyenDonationsFacade;
import com.adyen.commerce.facades.impl.DefaultAdyenCheckoutFacade;
import com.adyen.model.checkout.Amount;
import com.adyen.model.checkout.CardDonations;
import com.adyen.model.checkout.DonationCampaign;
import com.adyen.model.checkout.DonationCampaignsResponse;
import com.adyen.model.checkout.DonationPaymentMethod;
import com.adyen.model.checkout.DonationPaymentRequest;
import com.adyen.model.checkout.DonationPaymentResponse;
import com.adyen.model.checkout.PaymentResponse;
import com.adyen.service.exception.ApiException;
import de.hybris.platform.acceleratorservices.urlresolver.SiteBaseUrlResolutionService;
import de.hybris.platform.basecommerce.model.site.BaseSiteModel;
import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;
import de.hybris.platform.site.BaseSiteService;
import jakarta.annotation.Resource;
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
    public ResponseEntity<Map<String, Object>> donate(@RequestBody final DonationComponentRequest request) throws Exception {
        final String donationToken = sessionService.getAttribute(DefaultAdyenCheckoutFacade.SESSION_DONATION_TOKEN);
        final String originalPspReference = sessionService.getAttribute(DefaultAdyenCheckoutFacade.SESSION_DONATION_ORIGINAL_PSP_REFERENCE);
        final BaseStoreModel baseStore = baseStoreService.getCurrentBaseStore();

        if (request == null || request.getAmount() == null || request.getAmount().getValue() == null
                || request.getAmount().getValue() <= 0 || StringUtils.isAnyBlank(request.getCampaignId(),
                request.getAmount().getCurrency(), donationToken, originalPspReference)
                || baseStore == null || StringUtils.isBlank(baseStore.getAdyenMerchantAccount())) {
            return ResponseEntity.badRequest().build();
        }

        final DonationPaymentRequest donationRequest = new DonationPaymentRequest();
        donationRequest.setAmount(new Amount().currency(request.getAmount().getCurrency()).value(request.getAmount().getValue()));
        donationRequest.setDonationCampaignId(request.getCampaignId());
        donationRequest.setDonationToken(donationToken);
        donationRequest.setDonationOriginalPspReference(originalPspReference);
        donationRequest.setMerchantAccount(baseStore.getAdyenMerchantAccount());
        donationRequest.setReference(originalPspReference + "-donation-" + System.currentTimeMillis());
        donationRequest.setPaymentMethod(new DonationPaymentMethod(
                new CardDonations().type(CardDonations.TypeEnum.SCHEME)));
        donationRequest.setReturnUrl(getDonationReturnUrl());

        try {
            final DonationPaymentResponse response = adyenDonationsFacade.makeDonationPayment(donationRequest);
            final Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", response.getId());
            result.put("status", response.getStatus() == null ? null : response.getStatus().getValue());
            result.put("merchantAccount", response.getMerchantAccount());
            result.put("reference", response.getReference());
            if (response.getAmount() != null) {
                result.put("amount", amount(response.getAmount()));
            }
            if (response.getPayment() != null) {
                final PaymentResponse payment = response.getPayment();
                final Map<String, Object> paymentResult = new LinkedHashMap<>();
                paymentResult.put("pspReference", payment.getPspReference());
                paymentResult.put("resultCode", payment.getResultCode() == null ? null : payment.getResultCode().getValue());
                paymentResult.put("refusalReason", payment.getRefusalReason());
                paymentResult.put("refusalReasonCode", payment.getRefusalReasonCode());
                paymentResult.put("merchantReference", payment.getMerchantReference());
                if (payment.getAmount() != null) {
                    paymentResult.put("amount", amount(payment.getAmount()));
                }
                result.put("payment", paymentResult);
            }
            return ResponseEntity.ok(result);
        } catch (ApiException exception) {
            LOG.warn("Adyen donation request failed", exception);
            final Map<String, Object> result = new LinkedHashMap<>();
            result.put("error", exception.getMessage());
            result.put("statusCode", exception.getStatusCode());
            return ResponseEntity.status(exception.getStatusCode()).body(result);
        }
    }

    public static class DonationComponentRequest {
        private String campaignId;
        private DonationAmount amount;

        public String getCampaignId() { return campaignId; }
        public void setCampaignId(final String campaignId) { this.campaignId = campaignId; }
        public DonationAmount getAmount() { return amount; }
        public void setAmount(final DonationAmount amount) { this.amount = amount; }
    }

    protected String getDonationReturnUrl() {
        final BaseSiteModel currentBaseSite = baseSiteService.getCurrentBaseSite();
        return siteBaseUrlResolutionService.getWebsiteUrlForSite(currentBaseSite, true, "/");
    }

    protected Map<String, Object> amount(final Amount amount) {
        final Map<String, Object> amountResult = new LinkedHashMap<>();
        amountResult.put("currency", amount.getCurrency());
        amountResult.put("value", amount.getValue());
        return amountResult;
    }

    public static class DonationAmount {
        private String currency;
        private Long value;

        public String getCurrency() { return currency; }
        public void setCurrency(final String currency) { this.currency = currency; }
        public Long getValue() { return value; }
        public void setValue(final Long value) { this.value = value; }
    }
}
