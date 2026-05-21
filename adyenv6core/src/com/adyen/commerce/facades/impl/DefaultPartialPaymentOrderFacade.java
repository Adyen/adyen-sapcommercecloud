package com.adyen.commerce.facades.impl;

import com.adyen.commerce.facades.AdyenPartialPaymentOrderFacade;
import com.adyen.commerce.request.PartialPaymentOrderRequest;
import com.adyen.commerce.response.PartialPaymentOrderResponse;
import com.adyen.model.checkout.CreateOrderRequest;
import com.adyen.model.checkout.CreateOrderResponse;
import com.adyen.model.checkout.CancelOrderRequest;
import com.adyen.model.checkout.CancelOrderResponse;
import com.adyen.model.checkout.EncryptedOrderData;
import com.adyen.service.checkout.OrdersApi;
import com.adyen.service.exception.ApiException;
import com.adyen.v6.repository.AdyenPartialPaymentOrderRepository;
import com.adyen.v6.service.DefaultAdyenCheckoutApiService;
import com.adyen.v6.factory.AdyenPaymentServiceFactory;
import com.adyen.v6.enums.AdyenPartialPaymentStatus;
import com.adyen.v6.model.AdyenPartialPaymentOrderModel;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.commercefacades.order.CartFacade;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;
import org.apache.log4j.Logger;

import java.io.IOException;

import static com.adyen.v6.constants.Adyenv6coreConstants.*;

/**
 * Default implementation of AdyenOrderFacade
 * Handles business logic for Adyen order operations
 */
public class DefaultPartialPaymentOrderFacade implements AdyenPartialPaymentOrderFacade {
    
    private static final Logger LOG = Logger.getLogger(DefaultPartialPaymentOrderFacade.class);
    

    private CartFacade cartFacade;
    private BaseStoreService baseStoreService;
    private AdyenPaymentServiceFactory adyenPaymentServiceFactory;
    private ModelService modelService;
    private FlexibleSearchService flexibleSearchService;
    private AdyenPartialPaymentOrderRepository adyenPartialPaymentOrderRepository;
    
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
                throw new RuntimeException(PARTIAL_PAYMENT_ERROR_NO_ACTIVE_CART);
            }
            
            // Find existing partial payment order if partialPaymentId is provided
            AdyenPartialPaymentOrderModel partialPaymentOrder = null;
            if (request.getPartialPaymentId() != null) {
                partialPaymentOrder = adyenPartialPaymentOrderRepository.findPartialPaymentOrderByPspReference(request.getPartialPaymentId());
                if (partialPaymentOrder == null) {
                    LOG.error("Partial payment order not found for ID: " + request.getPartialPaymentId());
                    throw new RuntimeException(PARTIAL_PAYMENT_ERROR_ORDER_NOT_FOUND);
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
            throw new RuntimeException(PARTIAL_PAYMENT_ERROR_PAYMENT_SERVICE + ": " + e.getMessage());
        } catch (IOException e) {
            LOG.error("IO error during partial payment order creation", e);
            throw new RuntimeException(PARTIAL_PAYMENT_ERROR_COMMUNICATION);
        } catch (Exception e) {
            LOG.error("Unexpected error during partial payment order creation", e);
            throw new RuntimeException(PARTIAL_PAYMENT_ERROR_INTERNAL_SERVER + ": " + e.getMessage());
        }
    }
    
    /**
     * Create a new OrdersApi instance. Protected to allow overriding in tests.
     */
    protected OrdersApi createOrdersApi(DefaultAdyenCheckoutApiService adyenService) {
        return new OrdersApi(adyenService.getClient());
    }

    /**
     * Cancel a partial payment order via Adyen /orders/cancel endpoint.
     * This releases the held amount back to the gift card when the secondary payment fails.
     */
    @Override
    public void cancelPartialPaymentOrder(String pspReference) {
        LOG.debug("Cancelling partial payment order with PSP reference: " + pspReference);

        if (pspReference == null || pspReference.trim().isEmpty()) {
            LOG.error("Cannot cancel partial payment order: PSP reference is null or empty");
            throw new IllegalArgumentException("PSP reference must not be null or empty");
        }

        try {
            // Find the partial payment order in the database
            AdyenPartialPaymentOrderModel partialPaymentOrder =
                    adyenPartialPaymentOrderRepository.findPartialPaymentOrderByPspReference(pspReference);

            if (partialPaymentOrder == null) {
                LOG.warn("Partial payment order not found for PSP reference: " + pspReference + " — may have already been cleaned up");
                return;
            }

            // Get base store and create Adyen service
            BaseStoreModel baseStore = getBaseStoreService().getCurrentBaseStore();
            DefaultAdyenCheckoutApiService adyenService = (DefaultAdyenCheckoutApiService)
                getAdyenPaymentServiceFactory().createAdyenCheckoutApiService(baseStore);

            // Create OrdersApi instance
            OrdersApi ordersApi = createOrdersApi(adyenService);

            // Build cancel order request
            CancelOrderRequest cancelOrderRequest = new CancelOrderRequest();
            cancelOrderRequest.setMerchantAccount(baseStore.getAdyenMerchantAccount());

            // Set the order reference via EncryptedOrderData
            // The Adyen /orders/cancel API requires the pspReference and optionally orderData
            EncryptedOrderData encryptedOrderData = new EncryptedOrderData();
            encryptedOrderData.setPspReference(pspReference);
            if (partialPaymentOrder.getOrderData() != null) {
                encryptedOrderData.setOrderData(partialPaymentOrder.getOrderData());
            }
            cancelOrderRequest.setOrder(encryptedOrderData);

            LOG.debug("Sending cancel order request to Adyen for PSP reference: " + pspReference);

            // Call Adyen API
            CancelOrderResponse adyenResponse = ordersApi.cancelOrder(cancelOrderRequest);

            LOG.debug("Received cancel order response from Adyen: " + adyenResponse);

            // Update partial payment order status to CANCELLED
            partialPaymentOrder.setStatus(AdyenPartialPaymentStatus.CANCELLED);
            partialPaymentOrder.setProcessedAt(new java.util.Date());
            getModelService().save(partialPaymentOrder);

            LOG.info("Successfully cancelled partial payment order with PSP reference: " + pspReference +
                    ", Adyen response result code: " + (adyenResponse.getResultCode() != null ? adyenResponse.getResultCode().getValue() : "null"));

        } catch (ApiException e) {
            LOG.error("Adyen API error during partial payment order cancellation for PSP reference: " + pspReference, e);
            throw new RuntimeException(PARTIAL_PAYMENT_ERROR_PAYMENT_SERVICE + ": " + e.getMessage());
        } catch (IOException e) {
            LOG.error("IO error during partial payment order cancellation for PSP reference: " + pspReference, e);
            throw new RuntimeException(PARTIAL_PAYMENT_ERROR_COMMUNICATION);
        } catch (Exception e) {
            LOG.error("Unexpected error during partial payment order cancellation for PSP reference: " + pspReference, e);
            throw new RuntimeException(PARTIAL_PAYMENT_ERROR_INTERNAL_SERVER + ": " + e.getMessage());
        }
    }

    /**
     * Validate the partial payment order request
     */
    protected void validateRequest(PartialPaymentOrderRequest request) {
        if (request.getAmount() == null) {
            LOG.error("Amount is missing from partial payment order request");
            throw new RuntimeException(PARTIAL_PAYMENT_ERROR_AMOUNT_REQUIRED);
        }
        
        if (request.getAmount().getValue() == null || request.getAmount().getValue() <= 0) {
            LOG.error("Invalid amount value: " + request.getAmount().getValue());
            throw new RuntimeException(PARTIAL_PAYMENT_ERROR_AMOUNT_VALUE_REQUIRED);
        }
        
        if (request.getAmount().getCurrency() == null || request.getAmount().getCurrency().trim().isEmpty()) {
            LOG.error("Currency is missing from amount");
            throw new RuntimeException(PARTIAL_PAYMENT_ERROR_CURRENCY_REQUIRED);
        }
    }

    /**
     * Build the create order request for Adyen
     */
    protected CreateOrderRequest buildCreateOrderRequest(PartialPaymentOrderRequest request, BaseStoreModel baseStore) {
        CreateOrderRequest createOrderRequest = new CreateOrderRequest();
        createOrderRequest.setAmount(request.getAmount());
        createOrderRequest.setMerchantAccount(baseStore.getAdyenMerchantAccount());
        createOrderRequest.setReference(request.getPartialPaymentId());
        return createOrderRequest;
    }
    
    /**
     * Validate the Adyen response
     */
    protected void validateAdyenResponse(CreateOrderResponse adyenResponse) {
        if (adyenResponse.getOrderData() == null || adyenResponse.getOrderData().trim().isEmpty()) {
            LOG.error("Adyen response missing orderData: " + adyenResponse);
            throw new RuntimeException(PARTIAL_PAYMENT_ERROR_INVALID_RESPONSE + ": missing order data");
        }
        
        if (adyenResponse.getPspReference() == null || adyenResponse.getPspReference().trim().isEmpty()) {
            LOG.error("Adyen response missing pspReference: " + adyenResponse);
            throw new RuntimeException(PARTIAL_PAYMENT_ERROR_INVALID_RESPONSE + ": missing PSP reference");
        }
    }

    /**
     * Update partial payment order with Adyen response data
     */
    protected void updatePartialPaymentOrder(AdyenPartialPaymentOrderModel partialPaymentOrder, CreateOrderResponse adyenResponse) {
        partialPaymentOrder.setStatus(AdyenPartialPaymentStatus.CREATED);
        partialPaymentOrder.setProcessedAt(new java.util.Date());
        partialPaymentOrder.setOrderData(adyenResponse.getOrderData());
        getModelService().save(partialPaymentOrder);

        LOG.info("Updated partial payment order with PSP reference: " + partialPaymentOrder.getPspReference());
    }
    
    /**
     * Build the response from Adyen response
     */
    protected PartialPaymentOrderResponse buildResponse(CreateOrderResponse adyenResponse) {
        PartialPaymentOrderResponse response = new PartialPaymentOrderResponse();
        response.setOrderData(adyenResponse.getOrderData());
        response.setPspReference(adyenResponse.getPspReference());
        response.setResultCode("Success");
        return response;
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

    public AdyenPartialPaymentOrderRepository getAdyenPartialPaymentOrderRepository() {
        return adyenPartialPaymentOrderRepository;
    }

    public void setAdyenPartialPaymentOrderRepository(AdyenPartialPaymentOrderRepository adyenPartialPaymentOrderRepository) {
        this.adyenPartialPaymentOrderRepository = adyenPartialPaymentOrderRepository;
    }
}