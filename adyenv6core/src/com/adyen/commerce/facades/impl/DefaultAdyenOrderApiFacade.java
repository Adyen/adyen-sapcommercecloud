package com.adyen.commerce.facades.impl;

import com.adyen.commerce.facades.AdyenOrderApiFacade;
import com.adyen.commerce.request.PartialPaymentOrderRequest;
import com.adyen.commerce.response.PartialPaymentOrderResponse;
import com.adyen.model.checkout.CreateOrderRequest;
import com.adyen.model.checkout.CreateOrderResponse;
import com.adyen.service.checkout.OrdersApi;
import com.adyen.service.exception.ApiException;
import com.adyen.v6.service.DefaultAdyenCheckoutApiService;
import com.adyen.v6.factory.AdyenPaymentServiceFactory;
import com.adyen.v6.enums.AdyenPartialPaymentStatus;
import com.adyen.v6.model.AdyenPartialPaymentOrderModel;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.SearchResult;
import de.hybris.platform.commercefacades.order.CartFacade;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;
import org.apache.log4j.Logger;

import java.io.IOException;

/**
 * Default implementation of AdyenOrderFacade
 * Handles business logic for Adyen order operations
 */
public class DefaultAdyenOrderApiFacade implements AdyenOrderApiFacade {
    
    private static final Logger LOG = Logger.getLogger(DefaultAdyenOrderApiFacade.class);
    

    private CartFacade cartFacade;
    private BaseStoreService baseStoreService;
    private AdyenPaymentServiceFactory adyenPaymentServiceFactory;
    private ModelService modelService;
    private FlexibleSearchService flexibleSearchService;
    
    @Override
    public PartialPaymentOrderResponse createPartialPaymentOrder(PartialPaymentOrderRequest request) {
        LOG.debug("Creating partial payment order for amount: " + request.getAmount());
        
        // Validate required fields
        validateRequest(request);
        
        try {
            // Get current cart data
            CartData cartData = getCartFacade().getSessionCart();
            if (cartData == null) {
                LOG.error("No active cart found for partial payment order");
                throw new RuntimeException("No active cart found");
            }
            
            // Find existing partial payment order if partialPaymentId is provided
            AdyenPartialPaymentOrderModel partialPaymentOrder = null;
            if (request.getPartialPaymentId() != null) {
                partialPaymentOrder = findPartialPaymentOrderByPspReference(request.getPartialPaymentId());
                if (partialPaymentOrder == null) {
                    LOG.error("Partial payment order not found for ID: " + request.getPartialPaymentId());
                    throw new RuntimeException("Partial payment order not found");
                }
            }
            
            // Get base store and create Adyen service
            BaseStoreModel baseStore = getBaseStoreService().getCurrentBaseStore();
            DefaultAdyenCheckoutApiService adyenService = (DefaultAdyenCheckoutApiService)
                getAdyenPaymentServiceFactory().createAdyenCheckoutApiService(baseStore);
            
            // Create OrdersApi instance
            OrdersApi ordersApi = new OrdersApi(adyenService.getClient());
            
            // Build create order request
            CreateOrderRequest createOrderRequest = buildCreateOrderRequest(request, baseStore);
            
            LOG.debug("Sending create order request to Adyen: " + createOrderRequest);
            
            // Call Adyen API
            CreateOrderResponse adyenResponse = ordersApi.orders(createOrderRequest);
            
            LOG.debug("Received create order response from Adyen: " + adyenResponse);
            
            // Validate Adyen response
            validateAdyenResponse(adyenResponse);
            
            // Update partial payment order with Adyen response data
            if (partialPaymentOrder != null) {
                updatePartialPaymentOrder(partialPaymentOrder, adyenResponse);
            }
            
            // Build and return response
            PartialPaymentOrderResponse response = buildResponse(adyenResponse);
            
            LOG.info("Successfully created partial payment order with PSP reference: " + adyenResponse.getPspReference());
            
            return response;
            
        } catch (ApiException e) {
            LOG.error("Adyen API error during partial payment order creation", e);
            throw new RuntimeException("Payment service error: " + e.getMessage());
        } catch (IOException e) {
            LOG.error("IO error during partial payment order creation", e);
            throw new RuntimeException("Communication error with payment service");
        } catch (Exception e) {
            LOG.error("Unexpected error during partial payment order creation", e);
            throw new RuntimeException("Internal server error: " + e.getMessage());
        }
    }
    
    /**
     * Validate the partial payment order request
     */
    private void validateRequest(PartialPaymentOrderRequest request) {
        if (request.getAmount() == null) {
            LOG.error("Amount is missing from partial payment order request");
            throw new RuntimeException("Amount is required");
        }
        
        if (request.getAmount().getValue() == null || request.getAmount().getValue() <= 0) {
            LOG.error("Invalid amount value: " + request.getAmount().getValue());
            throw new RuntimeException("Valid amount value is required");
        }
        
        if (request.getAmount().getCurrency() == null || request.getAmount().getCurrency().trim().isEmpty()) {
            LOG.error("Currency is missing from amount");
            throw new RuntimeException("Currency is required");
        }
    }
    
    /**
     * Build the create order request for Adyen
     */
    private CreateOrderRequest buildCreateOrderRequest(PartialPaymentOrderRequest request, BaseStoreModel baseStore) {
        CreateOrderRequest createOrderRequest = new CreateOrderRequest();
        createOrderRequest.setAmount(request.getAmount());
        createOrderRequest.setMerchantAccount(baseStore.getAdyenMerchantAccount());
        
        // Generate unique reference
        String reference = "partial_payment_" + System.currentTimeMillis();
        if (request.getShopperReference() != null && !request.getShopperReference().trim().isEmpty()) {
            reference = "partial_payment_" + request.getShopperReference() + "_" + System.currentTimeMillis();
        }
        createOrderRequest.setReference(reference);
        
        return createOrderRequest;
    }
    
    /**
     * Validate the Adyen response
     */
    private void validateAdyenResponse(CreateOrderResponse adyenResponse) {
        if (adyenResponse.getOrderData() == null || adyenResponse.getOrderData().trim().isEmpty()) {
            LOG.error("Adyen response missing orderData: " + adyenResponse);
            throw new RuntimeException("Invalid response from payment service: missing order data");
        }
        
        if (adyenResponse.getPspReference() == null || adyenResponse.getPspReference().trim().isEmpty()) {
            LOG.error("Adyen response missing pspReference: " + adyenResponse);
            throw new RuntimeException("Invalid response from payment service: missing PSP reference");
        }
    }
    
    /**
     * Update partial payment order with Adyen response data
     */
    private void updatePartialPaymentOrder(AdyenPartialPaymentOrderModel partialPaymentOrder, CreateOrderResponse adyenResponse) {
        partialPaymentOrder.setPspReference(adyenResponse.getPspReference());
        //partialPaymentOrder.setOrderData(adyenResponse.getOrderData());
        partialPaymentOrder.setStatus(AdyenPartialPaymentStatus.AUTHORIZED);
        partialPaymentOrder.setProcessedAt(new java.util.Date());
        getModelService().save(partialPaymentOrder);
        
        LOG.info("Updated partial payment order with PSP reference: " + adyenResponse.getPspReference());
    }
    
    /**
     * Build the response from Adyen response
     */
    private PartialPaymentOrderResponse buildResponse(CreateOrderResponse adyenResponse) {
        PartialPaymentOrderResponse response = new PartialPaymentOrderResponse();
        response.setOrderData(adyenResponse.getOrderData());
        response.setPspReference(adyenResponse.getPspReference());
        response.setResultCode("Success");
        return response;
    }
    
    /**
     * Find partial payment order by PSP reference
     */
    private AdyenPartialPaymentOrderModel findPartialPaymentOrderByPspReference(String pspReference) {
        try {
            String query = "SELECT {pk} FROM {AdyenPartialPaymentOrder} WHERE {pspReference} = ?pspReference";
            FlexibleSearchQuery searchQuery = new FlexibleSearchQuery(query);
            searchQuery.addQueryParameter("pspReference", pspReference);
            
            SearchResult<AdyenPartialPaymentOrderModel> result = getFlexibleSearchService().search(searchQuery);
            if (result.getCount() > 0) {
                return result.getResult().get(0);
            }
        } catch (Exception e) {
            LOG.error("Error finding partial payment order by PSP reference: " + pspReference, e);
        }
        return null;
    }

    public CartFacade getCartFacade() {
        return cartFacade;
    }

    public void setCartFacade(CartFacade cartFacade) {
        this.cartFacade = cartFacade;
    }

    public BaseStoreService getBaseStoreService() {
        return baseStoreService;
    }

    public void setBaseStoreService(BaseStoreService baseStoreService) {
        this.baseStoreService = baseStoreService;
    }

    public AdyenPaymentServiceFactory getAdyenPaymentServiceFactory() {
        return adyenPaymentServiceFactory;
    }

    public void setAdyenPaymentServiceFactory(AdyenPaymentServiceFactory adyenPaymentServiceFactory) {
        this.adyenPaymentServiceFactory = adyenPaymentServiceFactory;
    }

    public ModelService getModelService() {
        return modelService;
    }

    public void setModelService(ModelService modelService) {
        this.modelService = modelService;
    }

    public FlexibleSearchService getFlexibleSearchService() {
        return flexibleSearchService;
    }

    public void setFlexibleSearchService(FlexibleSearchService flexibleSearchService) {
        this.flexibleSearchService = flexibleSearchService;
    }
}