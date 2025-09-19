package com.adyen.commerce.api.controllers.api;

import com.adyen.model.checkout.Amount;
import com.adyen.model.checkout.BalanceCheckRequest;
import com.adyen.model.checkout.BalanceCheckResponse;
import com.adyen.service.checkout.OrdersApi;
import com.adyen.service.exception.ApiException;
import com.adyen.v6.service.DefaultAdyenCheckoutApiService;
import com.adyen.v6.factory.AdyenPaymentServiceFactory;
import com.adyen.v6.service.AdyenPartialPaymentService;
import com.adyen.v6.model.AdyenPartialPaymentOrderModel;
import com.adyen.v6.enums.AdyenPartialPaymentStatus;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.order.CartService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.i18n.CommonI18NService;
import de.hybris.platform.core.model.c2l.CurrencyModel;
import de.hybris.platform.acceleratorstorefrontcommons.annotations.RequireHardLogIn;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping(value = "/api/giftcard")
public class AdyenGiftCardController {
    
    private static final Logger LOG = Logger.getLogger(AdyenGiftCardController.class);
    
    @Resource(name = "baseStoreService")
    private BaseStoreService baseStoreService;
    
    @Autowired
    private AdyenPaymentServiceFactory adyenPaymentServiceFactory;
    
    @Autowired
    private AdyenPartialPaymentService adyenPartialPaymentService;
    
    @Resource(name = "cartService")
    private CartService cartService;
    
    @Resource(name = "modelService")
    private ModelService modelService;
    
    @Resource(name = "commonI18NService")
    private CommonI18NService commonI18NService;
    
    /**
     * Request DTO for gift card balance check
     */
    public static class GiftCardBalanceRequest {
        private String cardNumber;
        private String pin;
        private Amount amount;
        private String brand;
        private String type;
        
        // Getters and setters
        public String getCardNumber() { return cardNumber; }
        public void setCardNumber(String cardNumber) { this.cardNumber = cardNumber; }
        public String getPin() { return pin; }
        public void setPin(String pin) { this.pin = pin; }
        public Amount getAmount() { return amount; }
        public void setAmount(Amount amount) { this.amount = amount; }

        public String getBrand() {
            return brand;
        }

        public void setBrand(String brand) {
            this.brand = brand;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }
    
    /**
     * Response DTO for gift card balance check
     */
    public static class GiftCardBalanceResponse {
        private Amount balance;
        private Amount transactionLimit;
        private String partialPaymentId;
        private java.math.BigDecimal chargedAmount;
        private java.math.BigDecimal remainingAmount;
        
        // Getters and setters
        public Amount getBalance() { return balance; }
        public void setBalance(Amount balance) { this.balance = balance; }
        public Amount getTransactionLimit() { return transactionLimit; }
        public void setTransactionLimit(Amount transactionLimit) { this.transactionLimit = transactionLimit; }
        public String getPartialPaymentId() { return partialPaymentId; }
        public void setPartialPaymentId(String partialPaymentId) { this.partialPaymentId = partialPaymentId; }
        public java.math.BigDecimal getChargedAmount() { return chargedAmount; }
        public void setChargedAmount(java.math.BigDecimal chargedAmount) { this.chargedAmount = chargedAmount; }
        public java.math.BigDecimal getRemainingAmount() { return remainingAmount; }
        public void setRemainingAmount(java.math.BigDecimal remainingAmount) { this.remainingAmount = remainingAmount; }
    }
    
    /**
     * Check gift card balance for partial payments
     * This endpoint is called to verify the available balance on a gift card before processing
     * 
     * @param request The gift card balance request containing card details and amount
     * @return Response containing available balance and transaction limit
     */
    @RequireHardLogIn
    @PostMapping(value = "/balance", produces = "application/json")
    public ResponseEntity<?> checkGiftCardBalance(@RequestBody GiftCardBalanceRequest request) {
        LOG.debug("Received gift card balance request: " +
            "cardNumber=" + (request.getCardNumber() != null ? "****" + request.getCardNumber().substring(Math.max(0, request.getCardNumber().length() - 4)) : "null") +
            ", pin=" + (request.getPin() != null ? "***" : "null") +
            ", amount=" + (request.getAmount() != null ? request.getAmount().getValue() + " " + request.getAmount().getCurrency() : "null"));
        
        // Validate required fields
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
        
        LOG.debug("Checking gift card balance for card ending in: " +
            request.getCardNumber().substring(Math.max(0, request.getCardNumber().length() - 4)));
        
        try {
            // Get base store and create Adyen service
            BaseStoreModel baseStore = baseStoreService.getCurrentBaseStore();
            DefaultAdyenCheckoutApiService adyenService = (DefaultAdyenCheckoutApiService) 
                adyenPaymentServiceFactory.createAdyenCheckoutApiService(baseStore);
            
            // Create OrdersApi instance
            OrdersApi ordersApi = new OrdersApi(adyenService.getClient());
            
            // Build balance check request
            BalanceCheckRequest balanceCheckRequest = new BalanceCheckRequest();
            balanceCheckRequest.setMerchantAccount(baseStore.getAdyenMerchantAccount());
            balanceCheckRequest.setAmount(request.getAmount());
            
            // Set payment method details for gift card
            Map<String, String> paymentMethod = new HashMap<>();
            paymentMethod.put("type", request.getType());
            paymentMethod.put("encryptedCardNumber", request.getCardNumber());
            paymentMethod.put("brand", request.getBrand());
            if (request.getPin() != null) {
                paymentMethod.put("encryptedSecurityCode", request.getPin());
            }
            balanceCheckRequest.setPaymentMethod(paymentMethod);
            
            LOG.debug("Sending balance check request to Adyen");
            
            // Call Adyen API
            BalanceCheckResponse adyenResponse = ordersApi.getBalanceOfGiftCard(balanceCheckRequest);
            
            LOG.debug("Received balance check response from Adyen: " + adyenResponse);
            
            // Calculate charged amount and remaining amount
            java.math.BigDecimal requestAmount = java.math.BigDecimal.valueOf(request.getAmount().getValue()).divide(java.math.BigDecimal.valueOf(100));
            java.math.BigDecimal availableBalance = java.math.BigDecimal.valueOf(adyenResponse.getBalance().getValue()).divide(java.math.BigDecimal.valueOf(100));
            java.math.BigDecimal transactionLimit = adyenResponse.getTransactionLimit() != null ?
                    java.math.BigDecimal.valueOf(adyenResponse.getTransactionLimit().getValue()).divide(java.math.BigDecimal.valueOf(100)) : null;
            
            java.math.BigDecimal chargedAmount = adyenPartialPaymentService.calculateChargedAmount(requestAmount, availableBalance, transactionLimit);
            java.math.BigDecimal remainingAmount = requestAmount.subtract(chargedAmount);
            
            // Create partial payment order entry to store balance information
            CartModel cartModel = cartService.getSessionCart();
            AdyenPartialPaymentOrderModel partialPaymentOrder = modelService.create(AdyenPartialPaymentOrderModel.class);
            
            // Store balance check information
            partialPaymentOrder.setRequestAmount(requestAmount);
            CurrencyModel currencyModel = commonI18NService.getCurrency(request.getAmount().getCurrency());
            partialPaymentOrder.setCurrency(currencyModel);
            partialPaymentOrder.setGiftCardBalance(availableBalance);
            if (transactionLimit != null) {
                partialPaymentOrder.setGiftCardTransactionLimit(transactionLimit);
            }
            partialPaymentOrder.setGiftCardChargedAmount(chargedAmount);
            partialPaymentOrder.setRemainingAmount(remainingAmount);
            
            // Store gift card details
            partialPaymentOrder.setGiftCardNumber(maskCardNumber(request.getCardNumber()));
            partialPaymentOrder.setGiftCardBrand(request.getBrand());
            partialPaymentOrder.setGiftCardType(request.getType());
            
            // Set status and associations
            partialPaymentOrder.setStatus(AdyenPartialPaymentStatus.CREATED);
            partialPaymentOrder.setPaymentMethod("giftcard");
            partialPaymentOrder.setCart(cartModel);
            
            // Set timestamps
            java.util.Date now = new java.util.Date();
            partialPaymentOrder.setCreatedAt(now);
            partialPaymentOrder.setBalanceCheckedAt(now);
            
            // Generate temporary PSP reference (will be updated when order is created)
            partialPaymentOrder.setPspReference("TEMP_" + System.currentTimeMillis());
            partialPaymentOrder.setOrderData(""); // Will be set when order is created
            
            modelService.save(partialPaymentOrder);
            
            LOG.info("Created partial payment order entry with temp PSP reference: " + partialPaymentOrder.getPspReference());
            
            // Build response
            GiftCardBalanceResponse response = new GiftCardBalanceResponse();
            response.setBalance(adyenResponse.getBalance());
            response.setTransactionLimit(adyenResponse.getTransactionLimit());
            response.setPartialPaymentId(partialPaymentOrder.getPspReference());
            response.setChargedAmount(chargedAmount);
            response.setRemainingAmount(remainingAmount);
            
            return ResponseEntity.ok(response);
            
        } catch (ApiException e) {
            LOG.error("Adyen API error during gift card balance check", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(createErrorResponse("Balance check failed: " + e.getMessage()));
        } catch (IOException e) {
            LOG.error("IO error during gift card balance check", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Communication error with payment service"));
        } catch (Exception e) {
            LOG.error("Unexpected error during gift card balance check", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Internal server error"));
        }
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
     * Mask card number for security (show only last 4 digits)
     */
    private String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            return "****";
        }
        return "****" + cardNumber.substring(cardNumber.length() - 4);
    }
}