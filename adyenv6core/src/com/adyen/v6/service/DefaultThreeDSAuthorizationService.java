/*
 *                        ######
 *                        ######
 *  ############    ####( ######  #####. ######  ############   ############
 *  #############  #####( ######  #####. ######  #############  #############
 *         ######  #####( ######  #####. ######  #####  ######  #####  ######
 *  ###### ######  #####( ######  #####. ######  #####  #####   #####  ######
 *  ###### ######  #####( ######  #####. ######  #####          #####  ######
 *  #############  #############  #############  #############  #####  ######
 *   ############   ############  #############   ############  #####  ######
 *                                       ######
 *                                #############
 *                                ############
 *
 *  Adyen SAP Commerce Extension
 *
 *  Copyright (c) 2025 Adyen B.V.
 *  This file is open source and available under the MIT license.
 *  See the LICENSE file for more info.
 */
package com.adyen.v6.service;

import com.adyen.model.checkout.PaymentDetailsRequest;
import com.adyen.model.checkout.PaymentDetailsResponse;
import com.adyen.service.exception.ApiException;
import com.adyen.v6.constants.Adyenv6coreConstants;
import com.adyen.v6.exceptions.AdyenNonAuthorizedPaymentException;
import com.adyen.v6.factory.AdyenPaymentServiceFactory;
import com.adyen.v6.repository.OrderRepository;
import de.hybris.platform.commercefacades.order.data.OrderData;
import de.hybris.platform.core.enums.OrderStatus;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.order.InvalidCartException;
import de.hybris.platform.servicelayer.dto.converter.Converter;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.store.services.BaseStoreService;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import java.util.Map;

import static com.adyen.constants.ApiConstants.ThreeDS2Property.THREEDS2_CHALLENGE_TOKEN;
import static com.adyen.constants.ApiConstants.ThreeDS2Property.THREEDS2_FINGERPRINT_TOKEN;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD;
import static com.adyen.v6.facades.impl.DefaultAdyenCheckoutFacade.SESSION_PENDING_ORDER_CODE;

/**
 * Default implementation of ThreeDSAuthorizationService.
 * This service handles all 3D Secure (3DS) related operations including authorization,
 * session management, and order status updates.
 */
public class DefaultThreeDSAuthorizationService implements ThreeDSAuthorizationService {

    private static final Logger LOGGER = Logger.getLogger(DefaultThreeDSAuthorizationService.class);

    private SessionService sessionService;
    private OrderRepository orderRepository;
    private AdyenPaymentServiceFactory adyenPaymentServiceFactory;
    private BaseStoreService baseStoreService;
    private ModelService modelService;
    private AdyenTransactionService adyenTransactionService;
    private AdyenOrderService adyenOrderService;
    private AdyenBusinessProcessService adyenBusinessProcessService;
    private Converter<OrderModel, OrderData> orderConverter;

    @Override
    public OrderData handle3DSResponse(PaymentDetailsRequest paymentsDetailsRequest) throws Exception {
        PaymentDetailsResponse paymentsDetailsResponse;
        try {
            paymentsDetailsResponse = authorize3DSPayment(paymentsDetailsRequest);
        } catch (Exception e) {
            LOGGER.error(e instanceof ApiException ? e.toString() : e.getMessage());
            throw new AdyenNonAuthorizedPaymentException(e.getMessage());
        }

        String orderCode = paymentsDetailsResponse.getMerchantReference();
        OrderModel orderModel = retrievePendingOrderAndClear3DSSession(orderCode);
        updateOrderPaymentStatusAndInfo(orderModel, paymentsDetailsResponse);

        PaymentDetailsResponse.ResultCodeEnum resultCode = paymentsDetailsResponse.getResultCode();

        if (PaymentDetailsResponse.ResultCodeEnum.AUTHORISED.equals(resultCode) || 
            PaymentDetailsResponse.ResultCodeEnum.RECEIVED.equals(resultCode)) {
            return orderConverter.convert(orderModel);
        }

        throw new AdyenNonAuthorizedPaymentException(paymentsDetailsResponse);
    }

    @Override
    public PaymentDetailsResponse authorize3DSPayment(PaymentDetailsRequest paymentsDetailsRequest) throws Exception {
        LOGGER.debug("Authorize 3DS payment");
        
        AdyenCheckoutApiService adyenPaymentService = adyenPaymentServiceFactory
            .createAdyenCheckoutApiService(baseStoreService.getCurrentBaseStore());
        
        return adyenPaymentService.authorise3DSPayment(paymentsDetailsRequest);
    }

    @Override
    public void updateOrderPaymentStatusAndInfo(OrderModel orderModel, PaymentDetailsResponse paymentDetailsResponse) {
        if (PaymentDetailsResponse.ResultCodeEnum.RECEIVED != paymentDetailsResponse.getResultCode()) {
            // Payment authorization is finished, update payment info
            LOGGER.debug("Payment authorization is finished, updating payment info");

            adyenTransactionService.createPaymentTransactionFromResultCode(orderModel,
                    orderModel.getCode(),
                    paymentDetailsResponse.getPspReference(),
                    paymentDetailsResponse.getResultCode());
        }

        if (PaymentDetailsResponse.ResultCodeEnum.AUTHORISED == paymentDetailsResponse.getResultCode() || 
            PaymentDetailsResponse.ResultCodeEnum.RECEIVED == paymentDetailsResponse.getResultCode()) {
            // PAYMENT_PENDING status, will be processed by order management
            LOGGER.info("PAYMENT_PENDING status, will be processed by order management");

            orderModel.setStatus(OrderStatus.PAYMENT_PENDING);
        } else {
            // Payment was not authorized, cancel pending order
            LOGGER.warn("Payment was not authorized, cancel pending order: " + paymentDetailsResponse.getPspReference());

            orderModel.setStatus(OrderStatus.CANCELLED);
            orderModel.setStatusInfo(paymentDetailsResponse.getPspReference() + " - " + 
                paymentDetailsResponse.getResultCode().getValue());
        }
        
        modelService.save(orderModel);
        adyenBusinessProcessService.triggerOrderProcessEvent(orderModel, 
            Adyenv6coreConstants.PROCESS_EVENT_ADYEN_PAYMENT_RESULT);

        String paymentType = "";
        if (paymentDetailsResponse.getPaymentMethod() != null) {
            paymentType = paymentDetailsResponse.getPaymentMethod().getType();
        }

        Map<String, String> additionalData = paymentDetailsResponse.getAdditionalData();

        adyenOrderService.updatePaymentInfo(orderModel, paymentType, additionalData);
        adyenOrderService.storeFraudReport(orderModel, paymentDetailsResponse.getPspReference(), 
            paymentDetailsResponse.getFraudResult());
    }

    @Override
    public void clear3DSSessionTokens() {
        sessionService.removeAttribute(THREEDS2_FINGERPRINT_TOKEN);
        sessionService.removeAttribute(THREEDS2_CHALLENGE_TOKEN);
        sessionService.removeAttribute(PAYMENT_METHOD);
    }

    @Override
    public OrderModel retrievePendingOrderAndClear3DSSession(String orderCode) throws Exception {
        if (StringUtils.isEmpty(orderCode)) {
            throw new InvalidCartException("Could not retrieve pending order: missing orderCode!");
        }

        OrderModel orderModel = orderRepository.getOrderModel(orderCode);
        if (orderModel == null) {
            throw new InvalidCartException("Order '" + orderCode + "' does not exist!");
        }

        sessionService.removeAttribute(SESSION_PENDING_ORDER_CODE);
        clear3DSSessionTokens();

        return orderModel;
    }

    // Getters and Setters
    public SessionService getSessionService() {
        return sessionService;
    }

    public void setSessionService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    public OrderRepository getOrderRepository() {
        return orderRepository;
    }

    public void setOrderRepository(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public AdyenPaymentServiceFactory getAdyenPaymentServiceFactory() {
        return adyenPaymentServiceFactory;
    }

    public void setAdyenPaymentServiceFactory(AdyenPaymentServiceFactory adyenPaymentServiceFactory) {
        this.adyenPaymentServiceFactory = adyenPaymentServiceFactory;
    }

    public BaseStoreService getBaseStoreService() {
        return baseStoreService;
    }

    public void setBaseStoreService(BaseStoreService baseStoreService) {
        this.baseStoreService = baseStoreService;
    }

    public ModelService getModelService() {
        return modelService;
    }

    public void setModelService(ModelService modelService) {
        this.modelService = modelService;
    }

    public AdyenTransactionService getAdyenTransactionService() {
        return adyenTransactionService;
    }

    public void setAdyenTransactionService(AdyenTransactionService adyenTransactionService) {
        this.adyenTransactionService = adyenTransactionService;
    }

    public AdyenOrderService getAdyenOrderService() {
        return adyenOrderService;
    }

    public void setAdyenOrderService(AdyenOrderService adyenOrderService) {
        this.adyenOrderService = adyenOrderService;
    }

    public AdyenBusinessProcessService getAdyenBusinessProcessService() {
        return adyenBusinessProcessService;
    }

    public void setAdyenBusinessProcessService(AdyenBusinessProcessService adyenBusinessProcessService) {
        this.adyenBusinessProcessService = adyenBusinessProcessService;
    }

    public Converter<OrderModel, OrderData> getOrderConverter() {
        return orderConverter;
    }

    public void setOrderConverter(Converter<OrderModel, OrderData> orderConverter) {
        this.orderConverter = orderConverter;
    }
}