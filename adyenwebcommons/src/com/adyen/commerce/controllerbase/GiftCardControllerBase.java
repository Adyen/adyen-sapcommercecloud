package com.adyen.commerce.controllerbase;

import com.adyen.commerce.exception.AdyenControllerException;
import com.adyen.commerce.facades.AdyenGiftCardFacade;
import com.adyen.commerce.request.GiftCardBalanceRequest;
import com.adyen.commerce.response.GiftCardBalanceResponse;
import org.apache.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static com.adyen.commerce.constants.AdyenwebcommonsConstants.CHECKOUT_ERROR_AUTHORIZATION_FAILED;

/**
 * Base controller class for Adyen gift card operations
 * Provides common functionality and delegates business logic to facade
 */
public abstract class GiftCardControllerBase {
    
    private static final Logger LOG = Logger.getLogger(GiftCardControllerBase.class);
    
    /**
     * Check gift card balance for partial payments
     * This endpoint is called to verify the available balance on a gift card before processing
     * 
     * @param request The gift card balance request containing card details and amount
     * @return Response containing available balance and transaction limit
     */
    public ResponseEntity<?> checkGiftCardBalance(GiftCardBalanceRequest request) {
        LOG.debug("Received gift card balance request: " +
            "cardNumber=" + (request.getCardNumber() != null ? "****" + request.getCardNumber().substring(Math.max(0, request.getCardNumber().length() - 4)) : "null") +
            ", pin=" + (request.getPin() != null ? "***" : "null") +
            ", amount=" + (request.getAmount() != null ? request.getAmount().getValue() + " " + request.getAmount().getCurrency() : "null"));
        
        // Validate required fields
        ResponseEntity<?> validationError = validateRequest(request);
        if (validationError != null) {
            return validationError;
        }
        
        try {
            // Delegate to facade for business logic
            GiftCardBalanceResponse response = getAdyenGiftCardFacade().checkGiftCardBalance(request);
            
            LOG.info("Gift card balance check completed successfully for card ending in: " +
                request.getCardNumber().substring(Math.max(0, request.getCardNumber().length() - 4)));
            
            return ResponseEntity.ok(response);
            
        } catch (RuntimeException e) {
            LOG.error("Error during gift card balance check", e);
            
            // Determine appropriate HTTP status based on exception message
            HttpStatus status = determineHttpStatus(e.getMessage());
            return ResponseEntity.status(status).body(createErrorResponse(e.getMessage()));
        } catch (Exception e) {
            LOG.error("Unexpected error during gift card balance check", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Internal server error"));
        }
    }
    
    /**
     * Validate the gift card balance request
     */
    private ResponseEntity<?> validateRequest(GiftCardBalanceRequest request) {
        if (request.getCardNumber() == null || request.getCardNumber().trim().isEmpty()) {
            LOG.error("Gift card number is missing or empty");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(createErrorResponse("Gift card number is required"));
        }
        
        if (request.getAmount() == null) {
            LOG.error("Amount is missing");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(createErrorResponse("Amount is required"));
        }
        
        if (request.getType() == null || request.getType().trim().isEmpty()) {
            LOG.error("Gift card type is missing");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(createErrorResponse("Gift card type is required"));
        }
        
        if (request.getBrand() == null || request.getBrand().trim().isEmpty()) {
            LOG.error("Gift card brand is missing");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(createErrorResponse("Gift card brand is required"));
        }
        
        return null; // No validation errors
    }
    
    /**
     * Determine HTTP status based on exception message
     */
    private HttpStatus determineHttpStatus(String errorMessage) {
        if (errorMessage != null) {
            if (errorMessage.contains("Balance check failed") || errorMessage.contains("API error")) {
                return HttpStatus.BAD_REQUEST;
            } else if (errorMessage.contains("Communication error")) {
                return HttpStatus.SERVICE_UNAVAILABLE;
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
    
    /**
     * Abstract method to get the gift card facade
     * Must be implemented by concrete controller classes
     */
    public abstract AdyenGiftCardFacade getAdyenGiftCardFacade();
}