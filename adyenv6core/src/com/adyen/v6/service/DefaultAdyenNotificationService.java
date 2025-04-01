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
 *  Adyen Hybris Extension
 *
 *  Copyright (c) 2017 Adyen B.V.
 *  This file is open source and available under the MIT license.
 *  See the LICENSE file for more info.
 */
package com.adyen.v6.service;

import com.adyen.model.notification.NotificationRequest;
import com.adyen.model.notification.NotificationRequestItem;
import com.adyen.notification.WebhookHandler;
import com.adyen.v6.constants.Adyenv6coreConstants;
import com.adyen.v6.model.AdyenNotificationModel;
import com.adyen.v6.model.NotificationItemModel;
import com.adyen.v6.repository.CartRepository;
import com.adyen.v6.repository.OrderRepository;
import com.adyen.v6.repository.PaymentTransactionRepository;
import com.google.gson.Gson;
import de.hybris.platform.commerceservices.order.CommercePlaceOrderStrategy;
import de.hybris.platform.core.enums.OrderStatus;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.payment.dto.TransactionStatus;
import de.hybris.platform.payment.dto.TransactionStatusDetails;
import de.hybris.platform.payment.enums.PaymentTransactionType;
import de.hybris.platform.payment.model.PaymentTransactionEntryModel;
import de.hybris.platform.payment.model.PaymentTransactionModel;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.session.SessionService;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.log4j.Logger;
import org.springframework.transaction.support.TransactionOperations;

import java.io.IOException;
import java.util.Date;
import java.util.UUID;

public class DefaultAdyenNotificationService implements AdyenNotificationService {
    public static final String PAYMENT_TRANSACTION_MODEL_IS_NULL_FOR_NOTIFICATION = "PaymentTransactionModel is null for notification: ";
    public static final String ORDER_WITH_ORDER_CODE = "Order with orderCode: ";
    public static final String CAUSE_AN_EXCEPTION = " cause an exception. \n";
    public static final String EXCEPTION_DURING_PROCESSING_NOTIFICATION = "Exception during processing notification: ";
    private ModelService modelService;
    private AdyenTransactionService adyenTransactionService;
    private AdyenBusinessProcessService adyenBusinessProcessService;
    private OrderRepository orderRepository;
    private PaymentTransactionRepository paymentTransactionRepository;
    private CartRepository cartRepository;
    private CommercePlaceOrderStrategy commercePlaceOrderStrategy;
    private SessionService sessionService;
    private TransactionOperations transactionTemplate;


    private static final Logger LOG = Logger.getLogger(DefaultAdyenNotificationService.class);

    @Override
    public NotificationItemModel createFromNotificationRequest(NotificationRequestItem notificationRequestItem) {
        Gson gson = new Gson();
        NotificationItemModel notificationItemModel = modelService.create(NotificationItemModel.class);

        if (notificationRequestItem.getAmount() != null) {
            notificationItemModel.setAmountCurrency(notificationRequestItem.getAmount().getCurrency());
            notificationItemModel.setAmountValue(notificationRequestItem.getAmount().getDecimalValue());
        }

        notificationItemModel.setUuid(UUID.randomUUID().toString());
        notificationItemModel.setEventCode(notificationRequestItem.getEventCode());
        notificationItemModel.setEventDate(notificationRequestItem.getEventDate());
        notificationItemModel.setMerchantAccountCode(notificationRequestItem.getMerchantAccountCode());
        notificationItemModel.setMerchantReference(notificationRequestItem.getMerchantReference());
        notificationItemModel.setOriginalReference(notificationRequestItem.getOriginalReference());
        notificationItemModel.setPspReference(notificationRequestItem.getPspReference());
        notificationItemModel.setReason(notificationRequestItem.getReason());
        notificationItemModel.setSuccess(notificationRequestItem.isSuccess());
        notificationItemModel.setPaymentMethod(notificationRequestItem.getPaymentMethod());

        String additionalDataJson = gson.toJson(notificationRequestItem.getAdditionalData());
        notificationItemModel.setAdditionalData(additionalDataJson);

        notificationItemModel.setCreatedAt(new Date());

        return notificationItemModel;
    }

    @Override
    public AdyenNotificationModel createNotificationInfoModel(NotificationRequestItem notificationRequestItem) {
        Gson gson = new Gson();
        AdyenNotificationModel adyenNotificationInfo = modelService.create(AdyenNotificationModel.class);

        if (notificationRequestItem.getAmount() != null) {
            adyenNotificationInfo.setAmountCurrency(notificationRequestItem.getAmount().getCurrency());
            adyenNotificationInfo.setAmountValue(notificationRequestItem.getAmount().getDecimalValue());
        }

        adyenNotificationInfo.setUuid(UUID.randomUUID().toString());
        adyenNotificationInfo.setEventCode(notificationRequestItem.getEventCode());
        adyenNotificationInfo.setEventDate(notificationRequestItem.getEventDate());
        adyenNotificationInfo.setMerchantAccountCode(notificationRequestItem.getMerchantAccountCode());
        adyenNotificationInfo.setMerchantReference(notificationRequestItem.getMerchantReference());
        adyenNotificationInfo.setOriginalReference(notificationRequestItem.getOriginalReference());
        adyenNotificationInfo.setPspReference(notificationRequestItem.getPspReference());
        adyenNotificationInfo.setReason(notificationRequestItem.getReason());
        adyenNotificationInfo.setSuccess(notificationRequestItem.isSuccess());
        adyenNotificationInfo.setPaymentMethod(notificationRequestItem.getPaymentMethod());

        String additionalDataJson = gson.toJson(notificationRequestItem.getAdditionalData());
        adyenNotificationInfo.setAdditionalData(additionalDataJson);

        adyenNotificationInfo.setCreatedAt(new Date());

        return adyenNotificationInfo;
    }

    @Override
    public void saveFromNotificationRequest(NotificationRequestItem notificationRequestItem) {
        NotificationItemModel notificationItemModel = createFromNotificationRequest(notificationRequestItem);

        modelService.save(notificationItemModel);
    }

    @Override
    public void saveNotifications(final NotificationRequest notificationRequest) {

        //Save the notification items to the database
        for (NotificationRequestItem notificationRequestItem : notificationRequest.getNotificationItems()) {
            saveFromNotificationRequest(notificationRequestItem);
        }
    }

    @Override
    public NotificationRequest getNotificationRequestFromString(final String requestString) throws IOException {
        WebhookHandler notificationHandler = new WebhookHandler();
        NotificationRequest notificationRequest = notificationHandler.handleNotificationJson(requestString);
        LOG.debug(notificationRequest);
        return notificationRequest;
    }

    @Override
    public PaymentTransactionEntryModel processCapturedEvent(AdyenNotificationModel notificationItemModel, PaymentTransactionModel paymentTransactionModel) {
        if (paymentTransactionModel == null) {
            LOG.error(PAYMENT_TRANSACTION_MODEL_IS_NULL_FOR_NOTIFICATION + notificationItemModel.getPspReference());
            return null;
        }
        OrderModel orderModel = (OrderModel) paymentTransactionModel.getOrder();
        try {
            PaymentTransactionEntryModel paymentTransactionEntryModel = transactionTemplate.execute(transactionStatus -> {
                //Register Captured transaction
                PaymentTransactionEntryModel paymentTransactionEntry = adyenTransactionService.createCapturedTransactionFromNotification(paymentTransactionModel, notificationItemModel);

                LOG.debug("Saving Captured transaction entry");
                modelService.save(paymentTransactionEntry);
                return paymentTransactionEntry;
            });
            //Trigger Captured event
            adyenBusinessProcessService.triggerOrderProcessEvent(orderModel, Adyenv6coreConstants.PROCESS_EVENT_ADYEN_CAPTURED);
            return paymentTransactionEntryModel;
        }catch (Exception e){
                LOG.error(ORDER_WITH_ORDER_CODE + orderModel.getCode() + CAUSE_AN_EXCEPTION);
                orderModel.setStatus(OrderStatus.PROCESSING_ERROR);
                orderModel.setStatusInfo(EXCEPTION_DURING_PROCESSING_NOTIFICATION + notificationItemModel.getPspReference());
                getModelService().save(orderModel);
                throw e;
            }
    }

    @Override
    public PaymentTransactionModel processAuthorisationEvent(AdyenNotificationModel notificationItemModel) {
        String orderCode = notificationItemModel.getMerchantReference();
        OrderModel orderModel = orderRepository.getOrderModel(orderCode);

        if (orderModel == null) {
            LOG.error(ORDER_WITH_ORDER_CODE + orderCode + " was not found!");
            return null;
        }

        PaymentTransactionModel paymentTransactionModel;
        try {
            if (BooleanUtils.isTrue(notificationItemModel.getSuccess())) {
                paymentTransactionModel = adyenTransactionService.authorizeOrderModel(orderModel, notificationItemModel.getMerchantReference(), notificationItemModel.getPspReference(), notificationItemModel.getAmountValue());
                LOG.debug("Payment authorization success");
            } else {
                paymentTransactionModel = adyenTransactionService.storeFailedAuthorizationFromNotification(notificationItemModel, orderModel);
            }
            adyenBusinessProcessService.triggerOrderProcessEvent(orderModel, Adyenv6coreConstants.PROCESS_EVENT_ADYEN_PAYMENT_RESULT);
            adyenBusinessProcessService.triggerOrderProcessEvent(orderModel, Adyenv6coreConstants.PROCESS_EVENT_ADYEN_CAPTURED);
            return paymentTransactionModel;
        } catch (Exception e){
            LOG.error(ORDER_WITH_ORDER_CODE + orderCode + CAUSE_AN_EXCEPTION);
            orderModel.setStatus(OrderStatus.PROCESSING_ERROR);
            orderModel.setStatusInfo(EXCEPTION_DURING_PROCESSING_NOTIFICATION + notificationItemModel.getPspReference());
            getModelService().save(orderModel);
            throw e;
        }
    }

    @Override
    public PaymentTransactionEntryModel processCancelEvent(AdyenNotificationModel notificationItemModel, PaymentTransactionModel paymentTransactionModel) {
        if (paymentTransactionModel == null) {
            LOG.error(PAYMENT_TRANSACTION_MODEL_IS_NULL_FOR_NOTIFICATION + notificationItemModel.getPspReference());
            return null;
        }

        return transactionTemplate.execute(transactionStatus -> {
            PaymentTransactionEntryModel paymentTransactionEntry = adyenTransactionService.createCancellationTransaction(paymentTransactionModel,
                    notificationItemModel.getMerchantReference(),
                    notificationItemModel.getPspReference());
            if (BooleanUtils.isTrue(notificationItemModel.getSuccess())) {
                paymentTransactionEntry.setTransactionStatusDetails(TransactionStatusDetails.SUCCESFULL.name());
                LOG.debug("Payment cancellation success");
            } else {
                paymentTransactionEntry.setTransactionStatusDetails(TransactionStatusDetails.UNKNOWN_CODE.name());
                LOG.warn("Payment cancellation failed for notification: " + notificationItemModel.getPspReference());
            }

            LOG.debug("Saving Cancel transaction entry");
            modelService.save(paymentTransactionEntry);
            return paymentTransactionEntry;
        });
    }

    @Override
    public PaymentTransactionEntryModel processRefundEvent(AdyenNotificationModel notificationItemModel) {
        PaymentTransactionModel paymentTransaction = paymentTransactionRepository.getTransactionModel(notificationItemModel.getOriginalReference());
        if (paymentTransaction == null) {
            LOG.error(PAYMENT_TRANSACTION_MODEL_IS_NULL_FOR_NOTIFICATION + notificationItemModel.getPspReference());
            return null;
        }

        return transactionTemplate.execute(transactionStatus -> {
            //Register Refund transaction
            PaymentTransactionEntryModel paymentTransactionEntryModel = adyenTransactionService.createRefundedTransactionFromNotification(paymentTransaction, notificationItemModel);

            LOG.debug("Saving Refunded transaction entry");
            modelService.save(paymentTransactionEntryModel);

            //Trigger Refunded event
            OrderModel orderModel = (OrderModel) paymentTransaction.getOrder();
            adyenBusinessProcessService.triggerReturnProcessEvent(orderModel, Adyenv6coreConstants.PROCESS_EVENT_ADYEN_REFUNDED);
            return paymentTransactionEntryModel;
        });
    }

    @Override
    public PaymentTransactionModel processOfferClosedEvent(AdyenNotificationModel notificationItemModel) {
        String orderCode = notificationItemModel.getMerchantReference();
        if(BooleanUtils.isFalse(notificationItemModel.getSuccess())) {
            LOG.error("Order " + orderCode + " received unexpected OFFER_CLOSED event with success=false");
            return null;
        }

        OrderModel orderModel = orderRepository.getOrderModel(orderCode);
        if (orderModel == null) {
            LOG.error("Order " + orderCode + " was not found, skipping OFFER_CLOSED event...");
            return null;
        }
        if (isOrderAuthorized(orderModel)) {
            LOG.error("Order " + orderCode + " already authorised, skipping OFFER_CLOSED event...");
            return null;
        }
        if (OrderStatus.CANCELLED.equals(orderModel.getStatus()) || OrderStatus.PROCESSING_ERROR.equals(orderModel.getStatus())) {
            LOG.error("Order " + orderCode + " already cancelled, skipping OFFER_CLOSED event...");
            return null;
        }

        orderModel.setStatus(OrderStatus.PROCESSING_ERROR);
        orderModel.setStatusInfo("Adyen OFFER_CLOSED: " + notificationItemModel.getPspReference());
        getModelService().save(orderModel);
        try {
            return adyenTransactionService.storeFailedAuthorizationFromNotification(notificationItemModel, orderModel);
        }catch (Exception e){
            LOG.error(ORDER_WITH_ORDER_CODE + orderCode + CAUSE_AN_EXCEPTION);
            orderModel.setStatus(OrderStatus.PROCESSING_ERROR);
            orderModel.setStatusInfo(EXCEPTION_DURING_PROCESSING_NOTIFICATION + notificationItemModel.getPspReference());
            getModelService().save(orderModel);
            throw e;
        }
    }

    protected boolean isTransactionAuthorized(final PaymentTransactionModel paymentTransactionModel) {
        for (final PaymentTransactionEntryModel entry : paymentTransactionModel.getEntries()) {
            if (entry.getType().equals(PaymentTransactionType.AUTHORIZATION)
                    && TransactionStatus.ACCEPTED.name().equals(entry.getTransactionStatus())) {
                return true;
            }
        }

        return false;
    }

    protected boolean isOrderAuthorized(final OrderModel order) {
        if(order.getPaymentTransactions() == null || order.getPaymentTransactions().isEmpty()) {
            return false;
        }

        //A single not authorized transaction means not authorized
        for (final PaymentTransactionModel paymentTransactionModel : order.getPaymentTransactions()) {
            if (!isTransactionAuthorized(paymentTransactionModel)) {
                return false;
            }
        }

        return true;
    }

    /**
     * @deprecated
     */
    @Override
    @Deprecated(since = "11.3.0")
    public void processNotification(NotificationItemModel notificationItemModel) {
        PaymentTransactionModel paymentTransaction;
        LOG.debug("Processing notification: "+ notificationItemModel.getPspReference()+" event: "+ notificationItemModel.getEventCode());

        switch (notificationItemModel.getEventCode()) {
            case NotificationRequestItem.EVENT_CODE_CAPTURE:
                paymentTransaction = paymentTransactionRepository.getTransactionModel(notificationItemModel.getOriginalReference());
                processCapturedEvent(convertFromNotificationItem(notificationItemModel), paymentTransaction);
                break;
            case NotificationRequestItem.EVENT_CODE_AUTHORISATION:
                paymentTransaction = paymentTransactionRepository.getTransactionModel(notificationItemModel.getPspReference());
                if (paymentTransaction == null) {
                    processAuthorisationEvent(convertFromNotificationItem(notificationItemModel));
                } else {
                    LOG.warn("Authorisation already processed " + paymentTransaction.getRequestId());
                }
                break;
            case NotificationRequestItem.EVENT_CODE_CANCEL_OR_REFUND:
                paymentTransaction = paymentTransactionRepository.getTransactionModel(notificationItemModel.getOriginalReference());
                processCancelEvent(convertFromNotificationItem(notificationItemModel), paymentTransaction);
                break;
            case NotificationRequestItem.EVENT_CODE_REFUND:
                processRefundEvent(convertFromNotificationItem(notificationItemModel));
                break;
            case NotificationRequestItem.EVENT_CODE_OFFER_CLOSED:
                processOfferClosedEvent(convertFromNotificationItem(notificationItemModel));
                break;
        }
    }

    protected AdyenNotificationModel convertFromNotificationItem(NotificationItemModel notificationItemModel){
        AdyenNotificationModel adyenNotificationInfo = new AdyenNotificationModel();
        adyenNotificationInfo.setAdditionalData(notificationItemModel.getAdditionalData());
        adyenNotificationInfo.setAmountCurrency(notificationItemModel.getAmountCurrency());
        adyenNotificationInfo.setAmountValue(notificationItemModel.getAmountValue());
        adyenNotificationInfo.setMerchantAccountCode(notificationItemModel.getMerchantAccountCode());
        adyenNotificationInfo.setOriginalReference(notificationItemModel.getOriginalReference());
        adyenNotificationInfo.setMerchantReference(notificationItemModel.getMerchantReference());
        adyenNotificationInfo.setSuccess(notificationItemModel.getSuccess());
        adyenNotificationInfo.setEventDate(notificationItemModel.getEventDate());
        adyenNotificationInfo.setAmountCurrency(notificationItemModel.getAmountCurrency());
        return adyenNotificationInfo;
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

    public AdyenBusinessProcessService getAdyenBusinessProcessService() {
        return adyenBusinessProcessService;
    }

    public void setAdyenBusinessProcessService(AdyenBusinessProcessService adyenBusinessProcessService) {
        this.adyenBusinessProcessService = adyenBusinessProcessService;
    }

    public OrderRepository getOrderRepository() {
        return orderRepository;
    }

    public void setOrderRepository(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public PaymentTransactionRepository getPaymentTransactionRepository() {
        return paymentTransactionRepository;
    }

    public void setPaymentTransactionRepository(PaymentTransactionRepository paymentTransactionRepository) {
        this.paymentTransactionRepository = paymentTransactionRepository;
    }

    public CartRepository getCartRepository() {
        return cartRepository;
    }

    public void setCartRepository(CartRepository cartRepository) {
        this.cartRepository = cartRepository;
    }

    public CommercePlaceOrderStrategy getCommercePlaceOrderStrategy() {
        return commercePlaceOrderStrategy;
    }

    public void setCommercePlaceOrderStrategy(CommercePlaceOrderStrategy commercePlaceOrderStrategy) {
        this.commercePlaceOrderStrategy = commercePlaceOrderStrategy;
    }

    public SessionService getSessionService() {
        return sessionService;
    }

    public void setSessionService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    public void setTransactionTemplate(TransactionOperations transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }
}
