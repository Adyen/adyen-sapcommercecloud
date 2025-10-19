package com.adyen.commerce.facades.impl;

import com.adyen.commerce.facades.AdyenGiftCardFacade;
import com.adyen.commerce.request.GiftCardBalanceRequest;
import com.adyen.commerce.response.GiftCardBalanceResponse;
import com.adyen.model.checkout.BalanceCheckRequest;
import com.adyen.model.checkout.BalanceCheckResponse;
import com.adyen.service.checkout.OrdersApi;
import com.adyen.service.exception.ApiException;
import com.adyen.v6.service.DefaultAdyenCheckoutApiService;
import com.adyen.v6.factory.AdyenPaymentServiceFactory;
import com.adyen.v6.service.AdyenPartialPaymentService;
import com.adyen.v6.model.AdyenPartialPaymentOrderModel;
import com.adyen.v6.enums.AdyenPartialPaymentStatus;
import com.adyen.v6.util.AmountUtil;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.order.CartService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.i18n.CommonI18NService;
import de.hybris.platform.core.model.c2l.CurrencyModel;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

/**
 * Default implementation of AdyenGiftCardFacade
 */
public class DefaultAdyenGiftCardFacade implements AdyenGiftCardFacade {
    
    private static final Logger LOG = Logger.getLogger(DefaultAdyenGiftCardFacade.class);
    

    private BaseStoreService baseStoreService;
    

    private AdyenPaymentServiceFactory adyenPaymentServiceFactory;
    

    private AdyenPartialPaymentService adyenPartialPaymentService;
    

    private CartService cartService;
    

    private ModelService modelService;
    

    private CommonI18NService commonI18NService;
    
    @Override
    public GiftCardBalanceResponse checkGiftCardBalance(GiftCardBalanceRequest request) {
        LOG.debug("Processing gift card balance request for card ending in: " +
            (request.getCardNumber() != null ? 
                request.getCardNumber().substring(Math.max(0, request.getCardNumber().length() - 4)) : "null"));
        
        try {
            // Get base store and create Adyen service
            BaseStoreModel baseStore = getBaseStoreService().getCurrentBaseStore();
            DefaultAdyenCheckoutApiService adyenService = (DefaultAdyenCheckoutApiService) 
                getAdyenPaymentServiceFactory().createAdyenCheckoutApiService(baseStore);
            
            // Create OrdersApi instance
            OrdersApi ordersApi = new OrdersApi(adyenService.getClient());
            
            // Build balance check request
            BalanceCheckRequest balanceCheckRequest = buildBalanceCheckRequest(baseStore, request);
            
            LOG.debug("Sending balance check request to Adyen");
            
            // Call Adyen API
            BalanceCheckResponse adyenResponse = ordersApi.getBalanceOfGiftCard(balanceCheckRequest);
            
            LOG.debug("Received balance check response from Adyen: " + adyenResponse);
            
            // Calculate amounts (convert from minor units to major currency units)

            BigDecimal requestAmount = AmountUtil.convertFromMinorUnits(request.getAmount().getValue(), request.getAmount().getCurrency());
            BigDecimal availableBalance = AmountUtil.convertFromMinorUnits(adyenResponse.getBalance().getValue(), adyenResponse.getBalance().getCurrency());
            BigDecimal transactionLimit = AmountUtil.convertFromMinorUnits(adyenResponse.getTransactionLimit() != null ? adyenResponse.getTransactionLimit().getValue() : null, adyenResponse.getTransactionLimit() != null ? adyenResponse.getTransactionLimit().getCurrency() : null);
            
            BigDecimal chargedAmount = getAdyenPartialPaymentService().calculateChargedAmount(requestAmount, availableBalance, transactionLimit);
            BigDecimal remainingAmount = requestAmount.subtract(chargedAmount);
            
            // Create and save partial payment order entry
            String partialPaymentId = createPartialPaymentOrder(request,adyenResponse, requestAmount, availableBalance,
                transactionLimit, chargedAmount, remainingAmount);
            
            // Build and return response
            return buildGiftCardBalanceResponse(adyenResponse, partialPaymentId, chargedAmount, remainingAmount);
            
        } catch (ApiException e) {
            LOG.error("Adyen API error during gift card balance check", e);
            throw new RuntimeException("Balance check failed: " + e.getMessage(), e);
        } catch (IOException e) {
            LOG.error("IO error during gift card balance check", e);
            throw new RuntimeException("Communication error with payment service", e);
        } catch (Exception e) {
            LOG.error("Unexpected error during gift card balance check", e);
            throw new RuntimeException("Internal server error", e);
        }
    }
    
    /**
     * Build balance check request for Adyen API
     */
    private BalanceCheckRequest buildBalanceCheckRequest(BaseStoreModel baseStore, GiftCardBalanceRequest request) {
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
        
        return balanceCheckRequest;
    }
    
    /**
     * Create partial payment order entry to store balance information
     */
    private String createPartialPaymentOrder(GiftCardBalanceRequest request, BalanceCheckResponse adyenResponse, BigDecimal requestAmount,
                                           BigDecimal availableBalance, BigDecimal transactionLimit,
                                           BigDecimal chargedAmount, BigDecimal remainingAmount) {
        CartModel cartModel = getCartService().getSessionCart();
        AdyenPartialPaymentOrderModel partialPaymentOrder = getModelService().create(AdyenPartialPaymentOrderModel.class);
        
        // Store balance check information
        partialPaymentOrder.setRequestAmount(requestAmount);
        CurrencyModel currencyModel = getCommonI18NService().getCurrency(request.getAmount().getCurrency());
        partialPaymentOrder.setCurrency(currencyModel);
        partialPaymentOrder.setGiftCardBalance(availableBalance);

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
        Date now = new Date();
        partialPaymentOrder.setCreatedAt(now);
        partialPaymentOrder.setBalanceCheckedAt(now);

        partialPaymentOrder.setPspReference(adyenResponse.getPspReference());


        getModelService().save(partialPaymentOrder);
        List<AdyenPartialPaymentOrderModel> newPartialPayments = new ArrayList<>();
        newPartialPayments.add(partialPaymentOrder);
        newPartialPayments.addAll(cartModel.getAdyenPartialPaymentOrders());
        cartModel.setAdyenPartialPaymentOrders(newPartialPayments);
        getModelService().save(cartModel);
        LOG.info("Created partial payment order entry with temp PSP reference: " + adyenResponse.getPspReference());

        return adyenResponse.getPspReference();
    }
    
    /**
     * Build gift card balance response
     */
    private GiftCardBalanceResponse buildGiftCardBalanceResponse(BalanceCheckResponse adyenResponse,
                                                               String partialPaymentId,
                                                               BigDecimal chargedAmount,
                                                               BigDecimal remainingAmount) {
        GiftCardBalanceResponse response = new GiftCardBalanceResponse();
        response.setBalance(adyenResponse.getBalance());
        response.setTransactionLimit(adyenResponse.getTransactionLimit());
        response.setPartialPaymentId(partialPaymentId);
        response.setChargedAmount(chargedAmount);
        response.setRemainingAmount(remainingAmount);
        
        return response;
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

    public AdyenPartialPaymentService getAdyenPartialPaymentService() {
        return adyenPartialPaymentService;
    }

    public void setAdyenPartialPaymentService(AdyenPartialPaymentService adyenPartialPaymentService) {
        this.adyenPartialPaymentService = adyenPartialPaymentService;
    }

    public CartService getCartService() {
        return cartService;
    }

    public void setCartService(CartService cartService) {
        this.cartService = cartService;
    }

    public ModelService getModelService() {
        return modelService;
    }

    public void setModelService(ModelService modelService) {
        this.modelService = modelService;
    }

    public CommonI18NService getCommonI18NService() {
        return commonI18NService;
    }

    public void setCommonI18NService(CommonI18NService commonI18NService) {
        this.commonI18NService = commonI18NService;
    }
}