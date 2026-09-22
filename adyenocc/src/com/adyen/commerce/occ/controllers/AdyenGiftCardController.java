/*
 * Copyright (c) 2021 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.adyen.commerce.occ.controllers;

import com.adyen.commerce.constants.AdyenoccConstants;
import com.adyen.commerce.controllerbase.GiftCardControllerBase;
import com.adyen.commerce.facades.AdyenGiftCardFacade;
import com.adyen.commerce.occ.api.AdyenGiftCardApi;
import com.adyen.commerce.request.GiftCardBalanceRequest;
import com.adyen.commerce.response.GiftCardBalanceResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.hybris.platform.commerceservices.request.mapping.annotation.ApiVersion;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ApiVersion("v2")
public class AdyenGiftCardController extends GiftCardControllerBase implements AdyenGiftCardApi {
    
    private static final Logger LOG = Logger.getLogger(AdyenGiftCardController.class);
    
    private static final String ERROR_CARD_NUMBER_REQUIRED = "Gift card number is required";
    private static final String ERROR_AMOUNT_REQUIRED = "Amount is required";
    private static final String ERROR_TYPE_REQUIRED = "Gift card type is required";
    private static final String ERROR_BRAND_REQUIRED = "Gift card brand is required";
    private static final String ERROR_BALANCE_CHECK_FAILED = "Unable to process gift card balance request";
    
    protected static ObjectMapper objectMapper;

    static {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }
    
    @Autowired
    private AdyenGiftCardFacade adyenGiftCardFacade;
    
    @Override
    @Secured({ "ROLE_CUSTOMERGROUP", "ROLE_TRUSTED_CLIENT", "ROLE_CUSTOMERMANAGERGROUP" })
    @PostMapping(value = AdyenoccConstants.ADYEN_USER_CART_PREFIX + "/giftcard/balance")
    public ResponseEntity<String> checkGiftCardBalance(@RequestBody GiftCardBalanceRequest request) {
        LOG.debug("Received gift card balance request: " +
            "cardNumber=" + (request.getCardNumber() != null ? "****" + request.getCardNumber().substring(Math.max(0, request.getCardNumber().length() - 4)) : "null") +
            ", pin=" + (request.getPin() != null ? "***" : "null") +
            ", amount=" + (request.getAmount() != null ? request.getAmount().getValue() + " " + request.getAmount().getCurrency() : "null"));
        
        // Validate required fields
        String validationError = validateRequestOCC(request);
        if (validationError != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(validationError);
        }
        
        try {
            // Delegate to facade for business logic
            GiftCardBalanceResponse response = adyenGiftCardFacade.checkGiftCardBalance(request);
            
            LOG.info("Gift card balance check completed successfully for card ending in: " +
                request.getCardNumber().substring(Math.max(0, request.getCardNumber().length() - 4)));
            
            String jsonResponse = objectMapper.writeValueAsString(response);
            return ResponseEntity.ok(jsonResponse);
            
        } catch (JsonProcessingException e) {
            LOG.error("Error serializing gift card balance response to JSON", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error processing response");
        } catch (RuntimeException e) {
            LOG.error("Error during gift card balance check", e);
            
            // Determine appropriate HTTP status based on exception message,
            // but return a generic message to avoid exposing internal error details.
            HttpStatus status = determineHttpStatus(e.getMessage());
            return ResponseEntity.status(status).body(ERROR_BALANCE_CHECK_FAILED);
        } catch (Exception e) {
            LOG.error("Unexpected error during gift card balance check", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Internal server error");
        }
    }
    
    /**
     * Validate the gift card balance request for OCC (returns String instead of ResponseEntity)
     */
    protected String validateRequestOCC(GiftCardBalanceRequest request) {
        if (request.getCardNumber() == null || request.getCardNumber().trim().isEmpty()) {
            LOG.error("Gift card number is missing or empty");
            return ERROR_CARD_NUMBER_REQUIRED;
        }
        
        if (request.getAmount() == null) {
            LOG.error("Amount is missing");
            return ERROR_AMOUNT_REQUIRED;
        }
        
        if (request.getType() == null || request.getType().trim().isEmpty()) {
            LOG.error("Gift card type is missing");
            return ERROR_TYPE_REQUIRED;
        }
        
        if (request.getBrand() == null || request.getBrand().trim().isEmpty()) {
            LOG.error("Gift card brand is missing");
            return ERROR_BRAND_REQUIRED;
        }
        
        return null; // No validation errors
    }
    
    @Override
    public AdyenGiftCardFacade getAdyenGiftCardFacade() {
        return adyenGiftCardFacade;
    }
}