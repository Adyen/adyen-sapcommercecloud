package com.adyen.commerce.api.controllers.api;

import com.adyen.commerce.facades.AdyenOrderApiFacade;
import com.adyen.commerce.request.PartialPaymentOrderRequest;
import com.adyen.commerce.response.PartialPaymentOrderResponse;
import de.hybris.platform.acceleratorstorefrontcommons.annotations.RequireHardLogIn;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping(value = "/api/orders")
public class AdyenOrderController {
    
    private static final Logger LOG = Logger.getLogger(AdyenOrderController.class);
    
    @Autowired
    private AdyenOrderApiFacade adyenOrderApiFacade;
    
    /**
     * Create a partial payment order for gift cards
     * This endpoint is called when a gift card needs to be processed as part of a partial payment flow
     *
     * @param request The partial payment order request containing amount and payment method details
     * @return Response containing order data and PSP reference
     */
    @RequireHardLogIn
    @PostMapping("/partial-payment")
    public ResponseEntity<?> createPartialPaymentOrder(@RequestBody PartialPaymentOrderRequest request) {
        LOG.debug("Received partial payment order request for amount: " +
            (request != null && request.getAmount() != null ? request.getAmount().getValue() + " " + request.getAmount().getCurrency() : "null"));
        
        // Validate required fields
        ResponseEntity<?> validationError = validateRequest(request);
        if (validationError != null) {
            return validationError;
        }
        
        try {
            // Delegate to facade for business logic
            PartialPaymentOrderResponse response = adyenOrderApiFacade.createPartialPaymentOrder(request);
            
            LOG.info("Partial payment order created successfully with PSP reference: " + response.getPspReference());
            
            return ResponseEntity.ok(response);
            
        } catch (RuntimeException e) {
            LOG.error("Error during partial payment order creation", e);
            
            // Determine appropriate HTTP status based on exception message
            HttpStatus status = determineHttpStatus(e.getMessage());
            return ResponseEntity.status(status).body(createErrorResponse(e.getMessage()));
        } catch (Exception e) {
            LOG.error("Unexpected error during partial payment order creation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Internal server error"));
        }
    }
    
    /**
     * Validate the partial payment order request
     */
    private ResponseEntity<?> validateRequest(PartialPaymentOrderRequest request) {
        if (request == null) {
            LOG.error("Request is null");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(createErrorResponse("Request body is required"));
        }
        
        if (request.getAmount() == null) {
            LOG.error("Amount is missing from partial payment order request");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(createErrorResponse("Amount is required"));
        }
        
        if (request.getAmount().getValue() == null || request.getAmount().getValue() <= 0) {
            LOG.error("Invalid amount value: " + request.getAmount().getValue());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(createErrorResponse("Valid amount value is required"));
        }
        
        if (request.getAmount().getCurrency() == null || request.getAmount().getCurrency().trim().isEmpty()) {
            LOG.error("Currency is missing from amount");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(createErrorResponse("Currency is required"));
        }
        
        return null; // No validation errors
    }
    
    /**
     * Determine HTTP status based on exception message
     */
    private HttpStatus determineHttpStatus(String errorMessage) {
        if (errorMessage != null) {
            if (errorMessage.contains("No active cart found") ||
                errorMessage.contains("Partial payment order not found") ||
                errorMessage.contains("Payment service error")) {
                return HttpStatus.BAD_REQUEST;
            } else if (errorMessage.contains("Communication error")) {
                return HttpStatus.SERVICE_UNAVAILABLE;
            } else if (errorMessage.contains("Amount is required") ||
                      errorMessage.contains("Valid amount value is required") ||
                      errorMessage.contains("Currency is required")) {
                return HttpStatus.BAD_REQUEST;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
    
    /**
     * Create error response map
     */
    private Map<String, String> createErrorResponse(String message) {
        Map<String, String> error = new HashMap<>();
        error.put("error", message);
        return error;
    }
}
