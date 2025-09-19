package com.adyen.v6.service.impl;

import com.adyen.model.checkout.Amount;
import com.adyen.model.checkout.BalanceCheckRequest;
import com.adyen.model.checkout.BalanceCheckResponse;
import com.adyen.model.checkout.CreateOrderRequest;
import com.adyen.model.checkout.CreateOrderResponse;
import com.adyen.service.checkout.OrdersApi;
import com.adyen.service.exception.ApiException;
import com.adyen.v6.enums.AdyenPartialPaymentStatus;
import com.adyen.v6.factory.AdyenPaymentServiceFactory;
import com.adyen.v6.model.AdyenPartialPaymentOrderModel;
import com.adyen.v6.service.AdyenPartialPaymentService;
import com.adyen.v6.service.DefaultAdyenCheckoutApiService;
import de.hybris.platform.core.model.c2l.CurrencyModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.servicelayer.i18n.CommonI18NService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Default implementation of AdyenPartialPaymentService
 */
public class DefaultAdyenPartialPaymentService implements AdyenPartialPaymentService {

    private static final Logger LOG = Logger.getLogger(DefaultAdyenPartialPaymentService.class);

    private BaseStoreService baseStoreService;
    private AdyenPaymentServiceFactory adyenPaymentServiceFactory;
    private ModelService modelService;
    private CommonI18NService commonI18NService;

    @Override
    public AdyenPartialPaymentOrderModel processPartialPayment(
            CartModel cartModel,
            String giftCardNumber,
            String giftCardPin,
            String giftCardBrand,
            String giftCardType,
            BigDecimal requestAmount,
            String currency) {

        LOG.info("Processing partial payment for cart: " + cartModel.getCode() + 
                ", amount: " + requestAmount + " " + currency);

        try {
            // Step 1: Check gift card balance
            Amount amountToCheck = new Amount();
            amountToCheck.setValue(requestAmount.longValue());
            amountToCheck.setCurrency(currency);

            BalanceCheckResult balanceResult = checkGiftCardBalance(
                    giftCardNumber, giftCardPin, giftCardBrand, giftCardType, amountToCheck);

            if (!balanceResult.isSuccess()) {
                throw new RuntimeException("Balance check failed: " + balanceResult.getErrorMessage());
            }

            // Step 2: Calculate actual charged amount
            BigDecimal availableBalance = BigDecimal.valueOf(balanceResult.getBalance().getValue()).divide(BigDecimal.valueOf(100));
            BigDecimal transactionLimit = balanceResult.getTransactionLimit() != null ? 
                    BigDecimal.valueOf(balanceResult.getTransactionLimit().getValue()).divide(BigDecimal.valueOf(100)) : null;

            BigDecimal chargedAmount = calculateChargedAmount(requestAmount, availableBalance, transactionLimit);
            BigDecimal remainingAmount = requestAmount.subtract(chargedAmount);

            LOG.info("Balance check result - Available: " + availableBalance + 
                    ", Charged: " + chargedAmount + ", Remaining: " + remainingAmount);

            // Step 3: Create order in Adyen for the charged amount
            Amount orderAmount = new Amount();
            orderAmount.setValue(chargedAmount.multiply(BigDecimal.valueOf(100)).longValue());
            orderAmount.setCurrency(currency);

            OrderCreationResult orderResult = createPartialPaymentOrder(orderAmount, currency);

            if (!orderResult.isSuccess()) {
                throw new RuntimeException("Order creation failed: " + orderResult.getErrorMessage());
            }

            // Step 4: Create and populate AdyenPartialPaymentOrderModel
            AdyenPartialPaymentOrderModel partialPaymentOrder = getModelService().create(AdyenPartialPaymentOrderModel.class);

            // Adyen Response Data
            partialPaymentOrder.setPspReference(orderResult.getPspReference());
            partialPaymentOrder.setOrderData(orderResult.getOrderData());

            // Request Information
            partialPaymentOrder.setRequestAmount(requestAmount);
            CurrencyModel currencyModel = getCommonI18NService().getCurrency(currency);
            partialPaymentOrder.setCurrency(currencyModel);

            // Balance Check Information
            partialPaymentOrder.setGiftCardBalance(availableBalance);
            if (transactionLimit != null) {
                partialPaymentOrder.setGiftCardTransactionLimit(transactionLimit);
            }
            partialPaymentOrder.setGiftCardChargedAmount(chargedAmount);
            partialPaymentOrder.setRemainingAmount(remainingAmount);

            // Gift Card Details
            partialPaymentOrder.setGiftCardNumber(maskCardNumber(giftCardNumber));
            partialPaymentOrder.setGiftCardBrand(giftCardBrand);
            partialPaymentOrder.setGiftCardType(giftCardType);

            // Status and Tracking
            partialPaymentOrder.setStatus(AdyenPartialPaymentStatus.CREATED);
            partialPaymentOrder.setPaymentMethod("giftcard");

            // Associations
            partialPaymentOrder.setCart(cartModel);

            // Timestamps
            Date now = new Date();
            partialPaymentOrder.setCreatedAt(now);
            partialPaymentOrder.setBalanceCheckedAt(now);

            // Save the model
            getModelService().save(partialPaymentOrder);

            LOG.info("Successfully created partial payment order with PSP reference: " + orderResult.getPspReference());

            return partialPaymentOrder;

        } catch (Exception e) {
            LOG.error("Error processing partial payment", e);
            throw new RuntimeException("Failed to process partial payment: " + e.getMessage(), e);
        }
    }

    @Override
    public BalanceCheckResult checkGiftCardBalance(
            String giftCardNumber,
            String giftCardPin,
            String giftCardBrand,
            String giftCardType,
            Amount amount) {

        LOG.debug("Checking gift card balance for card ending in: " + 
                giftCardNumber.substring(Math.max(0, giftCardNumber.length() - 4)));

        try {
            // Get base store and create Adyen service
            BaseStoreModel baseStore = getBaseStoreService().getCurrentBaseStore();
            DefaultAdyenCheckoutApiService adyenService = (DefaultAdyenCheckoutApiService) 
                    getAdyenPaymentServiceFactory().createAdyenCheckoutApiService(baseStore);

            // Create OrdersApi instance
            OrdersApi ordersApi = new OrdersApi(adyenService.getClient());

            // Build balance check request
            BalanceCheckRequest balanceCheckRequest = new BalanceCheckRequest();
            balanceCheckRequest.setMerchantAccount(baseStore.getAdyenMerchantAccount());
            balanceCheckRequest.setAmount(amount);

            // Set payment method details for gift card
            Map<String, String> paymentMethod = new HashMap<>();
            paymentMethod.put("type", giftCardType);
            paymentMethod.put("number", giftCardNumber);
            paymentMethod.put("brand", giftCardBrand);
            if (giftCardPin != null) {
                paymentMethod.put("cvc", giftCardPin);
            }
            balanceCheckRequest.setPaymentMethod(paymentMethod);

            LOG.debug("Sending balance check request to Adyen");

            // Call Adyen API
            BalanceCheckResponse adyenResponse = ordersApi.getBalanceOfGiftCard(balanceCheckRequest);

            LOG.debug("Received balance check response from Adyen: " + adyenResponse);

            return new BalanceCheckResult(
                    adyenResponse.getBalance(),
                    adyenResponse.getTransactionLimit(),
                    true,
                    null
            );

        } catch (ApiException e) {
            LOG.error("Adyen API error during gift card balance check", e);
            return new BalanceCheckResult(null, null, false, "Balance check failed: " + e.getMessage());
        } catch (IOException e) {
            LOG.error("IO error during gift card balance check", e);
            return new BalanceCheckResult(null, null, false, "Communication error with payment service");
        } catch (Exception e) {
            LOG.error("Unexpected error during gift card balance check", e);
            return new BalanceCheckResult(null, null, false, "Internal server error");
        }
    }

    @Override
    public OrderCreationResult createPartialPaymentOrder(Amount amount, String currency) {
        LOG.debug("Creating partial payment order for amount: " + amount.getValue() + " " + amount.getCurrency());

        try {
            // Get base store and create Adyen service
            BaseStoreModel baseStore = getBaseStoreService().getCurrentBaseStore();
            DefaultAdyenCheckoutApiService adyenService = (DefaultAdyenCheckoutApiService)
                    getAdyenPaymentServiceFactory().createAdyenCheckoutApiService(baseStore);

            // Create OrdersApi instance
            OrdersApi ordersApi = new OrdersApi(adyenService.getClient());

            // Build create order request
            CreateOrderRequest createOrderRequest = new CreateOrderRequest();
            createOrderRequest.setAmount(amount);
            createOrderRequest.setMerchantAccount(baseStore.getAdyenMerchantAccount());
            createOrderRequest.setReference("partial_payment_" + System.currentTimeMillis());

            LOG.debug("Sending create order request to Adyen: " + createOrderRequest);

            // Call Adyen API
            CreateOrderResponse adyenResponse = ordersApi.orders(createOrderRequest);

            LOG.debug("Received create order response from Adyen: " + adyenResponse);

            return new OrderCreationResult(
                    adyenResponse.getOrderData(),
                    adyenResponse.getPspReference(),
                    true,
                    null
            );

        } catch (ApiException e) {
            LOG.error("Adyen API error during partial payment order creation", e);
            return new OrderCreationResult(null, null, false, "Payment service error: " + e.getMessage());
        } catch (IOException e) {
            LOG.error("IO error during partial payment order creation", e);
            return new OrderCreationResult(null, null, false, "Communication error with payment service");
        } catch (Exception e) {
            LOG.error("Unexpected error during partial payment order creation", e);
            return new OrderCreationResult(null, null, false, "Internal server error");
        }
    }

    @Override
    public BigDecimal calculateChargedAmount(BigDecimal requestAmount, BigDecimal availableBalance, BigDecimal transactionLimit) {
        BigDecimal chargedAmount = requestAmount;

        // Use the minimum of requested amount and available balance
        if (availableBalance.compareTo(chargedAmount) < 0) {
            chargedAmount = availableBalance;
        }

        // Apply transaction limit if it exists and is lower
        if (transactionLimit != null && transactionLimit.compareTo(chargedAmount) < 0) {
            chargedAmount = transactionLimit;
        }

        LOG.debug("Calculated charged amount: " + chargedAmount + 
                " (requested: " + requestAmount + ", balance: " + availableBalance + 
                ", limit: " + transactionLimit + ")");

        return chargedAmount;
    }

    @Override
    public void updatePartialPaymentStatus(AdyenPartialPaymentOrderModel partialPaymentOrder, String newStatus) {
        try {
            AdyenPartialPaymentStatus statusEnum = AdyenPartialPaymentStatus.valueOf(newStatus);
            partialPaymentOrder.setStatus(statusEnum);
            partialPaymentOrder.setProcessedAt(new Date());
            getModelService().save(partialPaymentOrder);
            
            LOG.info("Updated partial payment order status to: " + newStatus + 
                    " for PSP reference: " + partialPaymentOrder.getPspReference());
        } catch (IllegalArgumentException e) {
            LOG.error("Invalid status: " + newStatus, e);
            throw new RuntimeException("Invalid status: " + newStatus);
        }
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