package com.adyen.commerce.occ.controllers;

import com.adyen.commerce.facades.AdyenDonationsFacade;
import com.adyen.commerce.occ.request.DonationRequest;
import com.adyen.model.checkout.Amount;
import com.adyen.model.checkout.DonationCampaignsResponse;
import com.adyen.model.checkout.DonationPaymentRequest;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

import java.util.logging.Logger;

import static com.adyen.commerce.constants.AdyenoccConstants.ADYEN_PREFIX;

@RestController
@RequestMapping(ADYEN_PREFIX + "/donations")
public class AdyenDonationsController {
    @Resource(name = "adyenDonationsFacade") private AdyenDonationsFacade adyenDonationsFacade;

    private static Logger logger = Logger.getLogger(String.valueOf(AdyenDonationsController.class));

    @Secured({"ROLE_CUSTOMERGROUP", "ROLE_TRUSTED_CLIENT", "ROLE_CUSTOMERMANAGERGROUP"})
    @GetMapping(value = "/campaigns", produces = "application/json")
    public ResponseEntity<DonationCampaignsResponse> campaigns() throws Exception {
        return ResponseEntity.ok(adyenDonationsFacade.getDonationCampaigns());
    }

//    @Secured({"ROLE_CUSTOMERGROUP", "ROLE_TRUSTED_CLIENT", "ROLE_CUSTOMERMANAGERGROUP"})
    @PostMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<String> donate(@Valid @RequestBody final DonationRequest request) throws Exception {
        logger.info("Received donation request for campaign: " + request.getDonationCampaignId());
        final DonationPaymentRequest donation = new DonationPaymentRequest();
        donation.setMerchantAccount(request.getMerchantAccount());
        donation.setReference(request.getReference());
        donation.setDonationCampaignId(request.getDonationCampaignId());
        donation.setDonationToken(request.getDonationToken());
        donation.setDonationOriginalPspReference(request.getDonationOriginalPspReference());
        donation.setReturnUrl(request.getReturnUrl());
        donation.setAmount(new Amount().currency(request.getAmount().getCurrency()).value(request.getAmount().getValue()));
        try {
             return ResponseEntity.ok().body(adyenDonationsFacade.makeDonationPayment(donation).toJson());
        }
        catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error occurred while processing donation: " + e.getMessage());
        }
    }
}
