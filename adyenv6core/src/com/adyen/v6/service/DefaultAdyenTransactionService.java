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

import com.adyen.model.checkout.PaymentDetailsResponse;
import com.adyen.v6.factory.AdyenPaymentServiceFactory;
import com.adyen.v6.model.AdyenNotificationModel;
import com.adyen.v6.repository.PaymentTransactionRepository;
import com.adyen.v6.util.AmountUtil;
import de.hybris.platform.core.model.c2l.CurrencyModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.payment.dto.TransactionStatus;
import de.hybris.platform.payment.dto.TransactionStatusDetails;
import de.hybris.platform.payment.enums.PaymentTransactionType;
import de.hybris.platform.payment.model.PaymentTransactionEntryModel;
import de.hybris.platform.payment.model.PaymentTransactionModel;
import de.hybris.platform.servicelayer.i18n.CommonI18NService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.store.services.BaseStoreService;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_PROVIDER;

public class DefaultAdyenTransactionService implements AdyenTransactionService {
    private static final Logger LOG = Logger.getLogger(DefaultAdyenTransactionService.class);

    private ModelService modelService;
    private CommonI18NService commonI18NService;
    private AdyenPaymentServiceFactory adyenPaymentServiceFactory;
    private BaseStoreService baseStoreService;
    private TransactionOperations transactionTemplate;
    private PaymentTransactionRepository paymentTransactionRepository;


    @Override
    public PaymentTransactionEntryModel createCapturedTransactionFromNotification(final PaymentTransactionModel paymentTransaction, final AdyenNotificationModel notificationItemModel) {
        final PaymentTransactionEntryModel transactionEntryModel = createFromModificationNotification(paymentTransaction, notificationItemModel);

        transactionEntryModel.setType(PaymentTransactionType.CAPTURE);

        return transactionEntryModel;
    }

    @Override
    public PaymentTransactionEntryModel createRefundedTransactionFromNotification(final PaymentTransactionModel paymentTransaction, final AdyenNotificationModel notificationItemModel) {
        final PaymentTransactionEntryModel transactionEntryModel = createFromModificationNotification(paymentTransaction, notificationItemModel);

        transactionEntryModel.setType(PaymentTransactionType.REFUND_FOLLOW_ON);

        return transactionEntryModel;
    }

    protected PaymentTransactionEntryModel createFromModificationNotification(final PaymentTransactionModel paymentTransaction, final AdyenNotificationModel notificationItemModel) {
        final PaymentTransactionEntryModel transactionEntryModel = modelService.create(PaymentTransactionEntryModel.class);

        String code = paymentTransaction.getRequestId() + "_" + paymentTransaction.getEntries().size();

        transactionEntryModel.setPaymentTransaction(paymentTransaction);
        transactionEntryModel.setRequestId(notificationItemModel.getPspReference());
        transactionEntryModel.setRequestToken(notificationItemModel.getMerchantReference());
        transactionEntryModel.setCode(code);
        transactionEntryModel.setTime(notificationItemModel.getEventDate());
        transactionEntryModel.setAmount(notificationItemModel.getAmountValue());

        String currencyCode = notificationItemModel.getAmountCurrency();
        final CurrencyModel currency = getCommonI18NService().getCurrency(currencyCode);
        transactionEntryModel.setCurrency(currency);

        if (BooleanUtils.isTrue(notificationItemModel.getSuccess())) {
            transactionEntryModel.setTransactionStatus(TransactionStatus.ACCEPTED.name());
            transactionEntryModel.setTransactionStatusDetails(TransactionStatusDetails.SUCCESFULL.name());
        } else {
            transactionEntryModel.setTransactionStatus(TransactionStatus.REJECTED.name());
            transactionEntryModel.setTransactionStatusDetails(TransactionStatusDetails.GENERAL_SYSTEM_ERROR.name());
        }

        return transactionEntryModel;
    }

    @Override
    public PaymentTransactionModel authorizeOrderModel(final AbstractOrderModel abstractOrderModel, final String merchantTransactionCode, final String pspReference) {
        return authorizeOrderModel(abstractOrderModel, merchantTransactionCode, pspReference, AmountUtil.calculateAmountWithTaxes(abstractOrderModel));
    }

    @Override
    public PaymentTransactionModel authorizeOrderModel(AbstractOrderModel abstractOrderModel, String merchantTransactionCode, String pspReference, BigDecimal paymentAmount) {
        return transactionTemplate.execute(transactionStatus -> {

            //First save the transactions to the CartModel < AbstractOrderModel
            PaymentTransactionModel paymentTransactionModel = paymentTransactionRepository.getTransactionModel(pspReference);

            if(paymentTransactionModel == null) {
                paymentTransactionModel = createPaymentTransaction(merchantTransactionCode, pspReference, abstractOrderModel, paymentAmount);
                modelService.save(paymentTransactionModel);
            }

            PaymentTransactionEntryModel authorisedTransaction = createAuthorizationPaymentTransactionEntryModel(paymentTransactionModel, merchantTransactionCode, abstractOrderModel, paymentAmount);

            LOG.info("Saving AUTH transaction entry with psp reference: " + pspReference);
            modelService.save(authorisedTransaction);

            modelService.refresh(paymentTransactionModel); //refresh is needed by order-process

            return paymentTransactionModel;
        });
    }

    @Override
    public PaymentTransactionModel storeFailedAuthorizationFromNotification(AdyenNotificationModel notificationItemModel, AbstractOrderModel abstractOrderModel) {
        LOG.warn("Payment authorization failed for order: " + notificationItemModel.getMerchantReference() + " notification reference: " + notificationItemModel.getPspReference());

        return transactionTemplate.execute(transactionStatus -> {

            boolean partialPayment = isPartialPayment(notificationItemModel, abstractOrderModel);

            //First save the transactions to the CartModel < AbstractOrderModel
            final PaymentTransactionModel paymentTransactionModel;
            if (partialPayment) {
                paymentTransactionModel = createPaymentTransaction(notificationItemModel.getMerchantReference(), notificationItemModel.getPspReference(), abstractOrderModel, notificationItemModel.getAmountValue());
            } else {
                paymentTransactionModel = createPaymentTransaction(notificationItemModel.getMerchantReference(), notificationItemModel.getPspReference(), abstractOrderModel);
            }

            modelService.save(paymentTransactionModel);

            final PaymentTransactionEntryModel authorisedTransaction;
            if (partialPayment) {
                authorisedTransaction = createAuthorizationPaymentTransactionEntryModel(paymentTransactionModel, notificationItemModel.getMerchantReference(), abstractOrderModel, notificationItemModel.getAmountValue());
            } else {
                authorisedTransaction = createAuthorizationPaymentTransactionEntryModel(paymentTransactionModel, notificationItemModel.getMerchantReference(), abstractOrderModel);
            }

            authorisedTransaction.setTransactionStatus(TransactionStatus.REJECTED.name());

            TransactionStatusDetails transactionStatusDetails = getTransactionStatusDetailsFromReason(notificationItemModel.getReason());
            authorisedTransaction.setTransactionStatusDetails(transactionStatusDetails.name());

            modelService.save(authorisedTransaction);
            return paymentTransactionModel;
        });
    }

    /**
     * Map notification item reason to transactionStatusDetails item
     */
    protected TransactionStatusDetails getTransactionStatusDetailsFromReason(String reason) {
        TransactionStatusDetails transactionStatusDetails = TransactionStatusDetails.UNKNOWN_CODE;

        if (reason != null) {
            switch (reason) {
                //TODO: fill more cases
                default:
                    transactionStatusDetails = TransactionStatusDetails.UNKNOWN_CODE;
                    break;
            }
        }

        return transactionStatusDetails;
    }

    protected PaymentTransactionEntryModel createAuthorizationPaymentTransactionEntryModel(final PaymentTransactionModel paymentTransaction, final String merchantCode, final AbstractOrderModel abstractOrderModel) {
        final PaymentTransactionEntryModel transactionEntryModel = modelService.create(PaymentTransactionEntryModel.class);

        String code = paymentTransaction.getRequestId() + "_" + paymentTransaction.getEntries().size();

        transactionEntryModel.setType(PaymentTransactionType.AUTHORIZATION);
        transactionEntryModel.setPaymentTransaction(paymentTransaction);
        transactionEntryModel.setRequestId(paymentTransaction.getRequestId());
        transactionEntryModel.setRequestToken(merchantCode);
        transactionEntryModel.setCode(code);
        transactionEntryModel.setTime(DateTime.now().toDate());
        transactionEntryModel.setTransactionStatus(TransactionStatus.ACCEPTED.name());
        transactionEntryModel.setTransactionStatusDetails(TransactionStatusDetails.SUCCESFULL.name());
        transactionEntryModel.setAmount(AmountUtil.calculateAmountWithTaxes(abstractOrderModel));
        transactionEntryModel.setCurrency(abstractOrderModel.getCurrency());

        return transactionEntryModel;
    }

    protected PaymentTransactionEntryModel createAuthorizationPaymentTransactionEntryModel(final PaymentTransactionModel paymentTransaction, final String merchantCode, final AbstractOrderModel abstractOrderModel, final BigDecimal paymentAmount) {
        final PaymentTransactionEntryModel transactionEntryModel = createAuthorizationPaymentTransactionEntryModel(paymentTransaction, merchantCode, abstractOrderModel);
        transactionEntryModel.setAmount(paymentAmount);
        return transactionEntryModel;
    }

    protected PaymentTransactionModel createPaymentTransaction(final String merchantCode, final String pspReference, final AbstractOrderModel abstractOrderModel) {
        return createPaymentTransaction(merchantCode, pspReference, abstractOrderModel, AmountUtil.calculateAmountWithTaxes(abstractOrderModel));
    }

    protected PaymentTransactionModel createPaymentTransaction(final String merchantCode, final String pspReference, final AbstractOrderModel abstractOrderModel, final BigDecimal paymentAmount) {
        final PaymentTransactionModel paymentTransactionModel = modelService.create(PaymentTransactionModel.class);
        paymentTransactionModel.setCode(pspReference);
        paymentTransactionModel.setRequestId(pspReference);
        paymentTransactionModel.setRequestToken(merchantCode);
        paymentTransactionModel.setPaymentProvider(PAYMENT_PROVIDER);
        paymentTransactionModel.setOrder(abstractOrderModel);
        paymentTransactionModel.setCurrency(abstractOrderModel.getCurrency());
        paymentTransactionModel.setInfo(abstractOrderModel.getPaymentInfo());
        paymentTransactionModel.setPlannedAmount(paymentAmount);
        return paymentTransactionModel;
    }

    @Override
    public PaymentTransactionEntryModel createCancellationTransaction(final PaymentTransactionModel paymentTransaction, final String merchantCode, final String pspReference) {
        final PaymentTransactionEntryModel transactionEntryModel = modelService.create(PaymentTransactionEntryModel.class);

        String code = paymentTransaction.getRequestId() + "_" + paymentTransaction.getEntries().size();

        transactionEntryModel.setType(PaymentTransactionType.CANCEL);
        transactionEntryModel.setPaymentTransaction(paymentTransaction);
        transactionEntryModel.setRequestId(pspReference);
        transactionEntryModel.setRequestToken(merchantCode);
        transactionEntryModel.setCode(code);
        transactionEntryModel.setTime(DateTime.now().toDate());
        transactionEntryModel.setTransactionStatus(TransactionStatus.ACCEPTED.name());
        transactionEntryModel.setTransactionStatusDetails(TransactionStatusDetails.REVIEW_NEEDED.name());
        transactionEntryModel.setAmount(paymentTransaction.getPlannedAmount());
        transactionEntryModel.setCurrency(paymentTransaction.getCurrency());

        return transactionEntryModel;
    }

    @Override
    public PaymentTransactionModel createPaymentTransactionFromResultCode(final AbstractOrderModel abstractOrderModel, final String merchantTransactionCode, final String pspReference, final PaymentDetailsResponse.ResultCodeEnum resultCodeEnum) {
        return transactionTemplate.execute(transactionStatus -> {

            PaymentTransactionModel paymentTransactionModel = paymentTransactionRepository.getTransactionModel(pspReference);

            if(paymentTransactionModel == null) {
                paymentTransactionModel = createPaymentTransaction(merchantTransactionCode, pspReference, abstractOrderModel);

                modelService.save(paymentTransactionModel);
            }

            PaymentTransactionEntryModel paymentTransactionEntryModel = createPaymentTransactionEntryModelFromResultCode(paymentTransactionModel, merchantTransactionCode, abstractOrderModel, resultCodeEnum);

            LOG.info("Saving transaction entry for resultCode " + resultCodeEnum + " with psp reference:" + pspReference);
            modelService.save(paymentTransactionEntryModel);

            List<PaymentTransactionEntryModel> entries = new ArrayList<>();
            entries.add(paymentTransactionEntryModel);
            paymentTransactionModel.setEntries(entries);
            modelService.refresh(paymentTransactionModel); //refresh is needed by order-process

            return paymentTransactionModel;
        });
    }

    protected PaymentTransactionEntryModel createPaymentTransactionEntryModelFromResultCode(final PaymentTransactionModel paymentTransaction, final String merchantCode, final AbstractOrderModel abstractOrderModel, final PaymentDetailsResponse.ResultCodeEnum resultCode) {
        final PaymentTransactionEntryModel transactionEntryModel = modelService.create(PaymentTransactionEntryModel.class);

        String code = paymentTransaction.getRequestId() + "_" + paymentTransaction.getEntries().size();

        transactionEntryModel.setType(PaymentTransactionType.AUTHORIZATION);
        transactionEntryModel.setPaymentTransaction(paymentTransaction);
        transactionEntryModel.setRequestId(paymentTransaction.getRequestId());
        transactionEntryModel.setRequestToken(merchantCode);
        transactionEntryModel.setCode(code);
        transactionEntryModel.setTime(DateTime.now().toDate());
        transactionEntryModel.setTransactionStatus(getTransactionStatusForResultCode(resultCode));
        transactionEntryModel.setTransactionStatusDetails("ResultCode: " + resultCode.getValue());
        transactionEntryModel.setAmount(AmountUtil.calculateAmountWithTaxes(abstractOrderModel));
        transactionEntryModel.setCurrency(abstractOrderModel.getCurrency());

        return transactionEntryModel;
    }

    protected String getTransactionStatusForResultCode(PaymentDetailsResponse.ResultCodeEnum resultCode) {
        switch (resultCode) {
            case AUTHORISED:
            case RECEIVED:
                return TransactionStatus.ACCEPTED.name();
            case REFUSED:
            case CANCELLED:
                return TransactionStatus.REJECTED.name();
            case ERROR:
                return TransactionStatus.ERROR.name();
            default:
                LOG.warn("Creating PaymentTransactionEntry for unexpected resultCode " + resultCode + ", saving as ERROR.");
                return TransactionStatus.ERROR.name();
        }
    }

    protected boolean isPartialPayment(AdyenNotificationModel notificationItemModel, AbstractOrderModel abstractOrderModel) {
        BigDecimal totalOrderAmount = AmountUtil.calculateAmountWithTaxes(abstractOrderModel);
        BigDecimal notificationAmount = notificationItemModel.getAmountValue();
        if (notificationAmount == null) {
            return false;
        }
        return totalOrderAmount.compareTo(notificationAmount) > 0;
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

    public void setTransactionTemplate(TransactionOperations transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }
}
