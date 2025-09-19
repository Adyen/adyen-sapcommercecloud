package com.adyen.commerce.api.controllers.api;

import com.adyen.model.checkout.Amount;
import com.adyen.model.checkout.CreateOrderRequest;
import com.adyen.model.checkout.CreateOrderResponse;
import com.adyen.service.checkout.OrdersApi;
import com.adyen.service.exception.ApiException;
import com.adyen.v6.service.DefaultAdyenCheckoutApiService;
import com.adyen.v6.factory.AdyenPaymentServiceFactory;
import com.adyen.v6.enums.AdyenPartialPaymentStatus;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.SearchResult;
import de.hybris.platform.acceleratorstorefrontcommons.annotations.RequireHardLogIn;
import com.adyen.v6.model.AdyenPartialPaymentOrderModel;
import de.hybris.platform.commercefacades.order.CartFacade;
import de.hybris.platform.commercefacades.order.data.CartData;
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
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping(value = "/api/orders")
public class AdyenOrderController {
    
    private static final Logger LOG = Logger.getLogger(AdyenOrderController.class);
    
    @Autowired
    private CartFacade cartFacade;
    
    @Resource(name = "baseStoreService")
    private BaseStoreService baseStoreService;
    
    @Autowired
    private AdyenPaymentServiceFactory adyenPaymentServiceFactory;
    
    @Resource(name = "modelService")
    private ModelService modelService;
    
    @Resource(name = "flexibleSearchService")
    private FlexibleSearchService flexibleSearchService;
    
    /**
     * Request DTO for partial payment order creation
     */
    public static class PartialPaymentOrderRequest {
        private Amount amount;
        private Map<String, Object> paymentMethod;
        private String shopperReference;
        private String partialPaymentId; // ID from balance check response
        
        // Getters and setters
        public Amount getAmount() { return amount; }
        public void setAmount(Amount amount) { this.amount = amount; }
        public Map<String, Object> getPaymentMethod() { return paymentMethod; }
        public void setPaymentMethod(Map<String, Object> paymentMethod) { this.paymentMethod = paymentMethod; }
        public String getShopperReference() { return shopperReference; }
        public void setShopperReference(String shopperReference) { this.shopperReference = shopperReference; }
        public String getPartialPaymentId() { return partialPaymentId; }
        public void setPartialPaymentId(String partialPaymentId) { this.partialPaymentId = partialPaymentId; }
    }
    
    /**
     * Response DTO for partial payment order creation
     */
    public static class PartialPaymentOrderResponse {
        private String orderData;
        private String pspReference;
        private String resultCode;
        
        // Getters and setters
        public String getOrderData() { return orderData; }
        public void setOrderData(String orderData) { this.orderData = orderData; }
        public String getPspReference() { return pspReference; }
        public void setPspReference(String pspReference) { this.pspReference = pspReference; }
        public String getResultCode() { return resultCode; }
        public void setResultCode(String resultCode) { this.resultCode = resultCode; }
    }
    
    
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
        LOG.debug("Creating partial payment order for amount: " + request.getAmount());
        
        // Validate required fields
        if (request.getAmount() == null) {
            LOG.error("Amount is missing from partial payment order request");
            return ResponseEntity.badRequest().body(createErrorResponse("Amount is required"));
        }
        
        if (request.getAmount().getValue() == null || request.getAmount().getValue() <= 0) {
            LOG.error("Invalid amount value: " + request.getAmount().getValue());
            return ResponseEntity.badRequest().body(createErrorResponse("Valid amount value is required"));
        }
        
        if (request.getAmount().getCurrency() == null || request.getAmount().getCurrency().trim().isEmpty()) {
            LOG.error("Currency is missing from amount");
            return ResponseEntity.badRequest().body(createErrorResponse("Currency is required"));
        }
        
        try {
            // Get current cart data
            CartData cartData = cartFacade.getSessionCart();
            if (cartData == null) {
                LOG.error("No active cart found for partial payment order");
                return ResponseEntity.badRequest().body(createErrorResponse("No active cart found"));
            }
            
            // Find existing partial payment order if partialPaymentId is provided
            AdyenPartialPaymentOrderModel partialPaymentOrder = null;
            if (request.getPartialPaymentId() != null) {
                partialPaymentOrder = findPartialPaymentOrderByPspReference(request.getPartialPaymentId());
                if (partialPaymentOrder == null) {
                    LOG.error("Partial payment order not found for ID: " + request.getPartialPaymentId());
                    return ResponseEntity.badRequest().body(createErrorResponse("Partial payment order not found"));
                }
            }
            
            // Get base store and create Adyen service
            BaseStoreModel baseStore = baseStoreService.getCurrentBaseStore();
            DefaultAdyenCheckoutApiService adyenService = (DefaultAdyenCheckoutApiService)
                adyenPaymentServiceFactory.createAdyenCheckoutApiService(baseStore);
            
            // Create OrdersApi instance
            OrdersApi ordersApi = new OrdersApi(adyenService.getClient());
            
            // Build create order request
            CreateOrderRequest createOrderRequest = new CreateOrderRequest();
            createOrderRequest.setAmount(request.getAmount());
            createOrderRequest.setMerchantAccount(baseStore.getAdyenMerchantAccount());
            
            // Generate unique reference
            String reference = "partial_payment_" + System.currentTimeMillis();
            if (request.getShopperReference() != null && !request.getShopperReference().trim().isEmpty()) {
                reference = "partial_payment_" + request.getShopperReference() + "_" + System.currentTimeMillis();
            }
            createOrderRequest.setReference(reference);
            
            LOG.debug("Sending create order request to Adyen: " + createOrderRequest);
            
            // Call Adyen API
            CreateOrderResponse adyenResponse = ordersApi.orders(createOrderRequest);
            
            LOG.debug("Received create order response from Adyen: " + adyenResponse);
            
            // Validate Adyen response
            if (adyenResponse.getOrderData() == null || adyenResponse.getOrderData().trim().isEmpty()) {
                LOG.error("Adyen response missing orderData: " + adyenResponse);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Invalid response from payment service: missing order data"));
            }
            
            if (adyenResponse.getPspReference() == null || adyenResponse.getPspReference().trim().isEmpty()) {
                LOG.error("Adyen response missing pspReference: " + adyenResponse);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Invalid response from payment service: missing PSP reference"));
            }
            
            // Update partial payment order with Adyen response data
            if (partialPaymentOrder != null) {
                partialPaymentOrder.setPspReference(adyenResponse.getPspReference());
                partialPaymentOrder.setOrderData(adyenResponse.getOrderData());
                partialPaymentOrder.setStatus(AdyenPartialPaymentStatus.AUTHORIZED);
                partialPaymentOrder.setProcessedAt(new java.util.Date());
                modelService.save(partialPaymentOrder);
                
                LOG.info("Updated partial payment order with PSP reference: " + adyenResponse.getPspReference());
            }
            
            // Build response
            PartialPaymentOrderResponse response = new PartialPaymentOrderResponse();
            response.setOrderData(adyenResponse.getOrderData());
            response.setPspReference(adyenResponse.getPspReference());
            response.setResultCode("Success");
            
            LOG.info("Successfully created partial payment order with PSP reference: " + adyenResponse.getPspReference());
            
            return ResponseEntity.ok(response);
            
        } catch (ApiException e) {
            LOG.error("Adyen API error during partial payment order creation", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(createErrorResponse("Payment service error: " + e.getMessage()));
        } catch (IOException e) {
            LOG.error("IO error during partial payment order creation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Communication error with payment service"));
        } catch (Exception e) {
            LOG.error("Unexpected error during partial payment order creation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Internal server error: " + e.getMessage()));
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
     * Find partial payment order by PSP reference
     */
    private AdyenPartialPaymentOrderModel findPartialPaymentOrderByPspReference(String pspReference) {
        try {
            String query = "SELECT {pk} FROM {AdyenPartialPaymentOrder} WHERE {pspReference} = ?pspReference";
            FlexibleSearchQuery searchQuery = new FlexibleSearchQuery(query);
            searchQuery.addQueryParameter("pspReference", pspReference);
            
            SearchResult<AdyenPartialPaymentOrderModel> result = flexibleSearchService.search(searchQuery);
            if (result.getCount() > 0) {
                return result.getResult().get(0);
            }
        } catch (Exception e) {
            LOG.error("Error finding partial payment order by PSP reference: " + pspReference, e);
        }
        return null;
    }
}
