/*
 * Copyright (c) 2021 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.adyen.commerce.occ.controllers;

import com.adyen.commerce.constants.AdyenoccConstants;
import com.adyen.commerce.facades.AdyenPartialPaymentOrderFacade;
import com.adyen.commerce.occ.api.AdyenPartialPaymentOrderApi;
import com.adyen.commerce.request.PartialPaymentOrderRequest;
import com.adyen.commerce.response.PartialPaymentOrderResponse;
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

import static com.adyen.v6.constants.Adyenv6coreConstants.*;

@RestController
@ApiVersion("v2")
public class AdyenPartialPaymentOrderController implements AdyenPartialPaymentOrderApi {
    
    private static final Logger LOG = Logger.getLogger(AdyenPartialPaymentOrderController.class);
    
    protected static ObjectMapper objectMapper;

    static {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }
    
    @Autowired
    private AdyenPartialPaymentOrderFacade adyenPartialPaymentOrderFacade;
    
    @Override
    @Secured({ "ROLE_CUSTOMERGROUP", "ROLE_TRUSTED_CLIENT", "ROLE_CUSTOMERMANAGERGROUP" })
    @PostMapping(value = AdyenoccConstants.ADYEN_USER_CART_PREFIX + "/orders/partial-payment")
    public ResponseEntity<String> createPartialPaymentOrder(@RequestBody PartialPaymentOrderRequest request) {
        LOG.debug("Received partial payment order request for amount: " +
            (request != null && request.getAmount() != null ? request.getAmount().getValue() + " " + request.getAmount().getCurrency() : "null"));
        
        // Validate required fields
        String validationError = validateRequest(request);
        if (validationError != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(validationError);
        }
        
        try {
            PartialPaymentOrderResponse response = adyenPartialPaymentOrderFacade.createPartialPaymentOrder(request);
            
            LOG.info("Partial payment order created successfully with PSP reference: " + response.getPspReference());
            
            String jsonResponse = objectMapper.writeValueAsString(response);
            return ResponseEntity.ok(jsonResponse);
            
        } catch (RuntimeException e) {
            LOG.error("Error during partial payment order creation", e);
            
            // Determine appropriate HTTP status based on exception message
            HttpStatus status = determineHttpStatus(e.getMessage());
            return ResponseEntity.status(status).body(e.getMessage());
        } catch (Exception e) {
            LOG.error("Unexpected error during partial payment order creation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(PARTIAL_PAYMENT_ERROR_INTERNAL_SERVER);
        }
    }
    
    /**
     * Validate the partial payment order request
     */
    protected String validateRequest(PartialPaymentOrderRequest request) {
        if (request == null) {
            LOG.error("Request is null");
            return PARTIAL_PAYMENT_ERROR_REQUEST_REQUIRED;
        }
        
        if (request.getAmount() == null) {
            LOG.error("Amount is missing from partial payment order request");
            return PARTIAL_PAYMENT_ERROR_AMOUNT_REQUIRED;
        }
        
        if (request.getAmount().getValue() == null || request.getAmount().getValue() <= 0) {
            LOG.error("Invalid amount value: " + request.getAmount().getValue());
            return PARTIAL_PAYMENT_ERROR_AMOUNT_VALUE_REQUIRED;
        }
        
        if (request.getAmount().getCurrency() == null || request.getAmount().getCurrency().trim().isEmpty()) {
            LOG.error("Currency is missing from amount");
            return PARTIAL_PAYMENT_ERROR_CURRENCY_REQUIRED;
        }
        
        return null; // No validation errors
    }
    
    /**
     * Determine HTTP status based on exception message
     */
    protected HttpStatus determineHttpStatus(String errorMessage) {
        if (errorMessage != null && !errorMessage.isEmpty()) {
            if (errorMessage.contains(PARTIAL_PAYMENT_ERROR_COMMUNICATION)) {
                return HttpStatus.SERVICE_UNAVAILABLE;
            } else {
                return HttpStatus.BAD_REQUEST;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}