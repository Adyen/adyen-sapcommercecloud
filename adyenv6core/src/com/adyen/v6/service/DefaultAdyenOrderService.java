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

import com.adyen.model.checkout.FraudCheckResult;
import com.adyen.model.checkout.FraudResult;
import de.hybris.platform.basecommerce.enums.FraudStatus;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.order.payment.PaymentInfoModel;
import de.hybris.platform.fraud.model.FraudReportModel;
import de.hybris.platform.fraud.model.FraudSymptomScoringModel;
import de.hybris.platform.servicelayer.model.ModelService;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.transaction.support.TransactionOperations;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_PROVIDER;

public class DefaultAdyenOrderService implements AdyenOrderService {
    private static final Logger LOG = Logger.getLogger(DefaultAdyenOrderService.class);
    private ModelService modelService;
    private TransactionOperations transactionTemplate;

    private final SimpleDateFormat expDateFormatter = new SimpleDateFormat("M/yyyy");

    @Override
    public FraudReportModel createFraudReportFromPaymentsResponse(String pspReference, FraudResult fraudResult) {
        FraudReportModel fraudReport = modelService.create(FraudReportModel.class);

        if (fraudResult == null) {
            LOG.warn("No fraud result found");
            return null;
        }

        fraudReport.setCode(pspReference);
        fraudReport.setStatus(FraudStatus.OK);
        fraudReport.setExplanation("Score: " + fraudResult.getAccountScore());
        fraudReport.setTimestamp(new Date());
        fraudReport.setProvider(PAYMENT_PROVIDER);

        List<FraudSymptomScoringModel> fraudSymptomScorings = new ArrayList<>();
        for (FraudCheckResult fraudCheckResult : fraudResult.getResults()) {
            FraudSymptomScoringModel fraudSymptomScoring = modelService.create(FraudSymptomScoringModel.class);

            Integer score = fraudCheckResult.getAccountScore();
            if (score != null) {
                fraudSymptomScoring.setScore(score.doubleValue());
            } else {
                fraudSymptomScoring.setScore(0.0);
            }

            fraudSymptomScoring.setName(fraudCheckResult.getName());
            fraudSymptomScoring.setExplanation("Check id: " + fraudCheckResult.getCheckId());
            fraudSymptomScoring.setFraudReport(fraudReport);

            fraudSymptomScorings.add(fraudSymptomScoring);
        }

        fraudReport.setFraudSymptomScorings(fraudSymptomScorings);

        LOG.info("Returning fraud report with score: " + fraudResult.getAccountScore());

        return fraudReport;

    }

    @Override
    public void storeFraudReport(FraudReportModel fraudReport) {
        transactionTemplate.execute(transactionStatus -> {
            List<FraudSymptomScoringModel> fraudSymptomScorings = fraudReport.getFraudSymptomScorings();
            modelService.save(fraudReport);

            for (FraudSymptomScoringModel fraudSymptomScoring : fraudSymptomScorings) {
                modelService.save(fraudSymptomScoring);
            }
            return null;
        });
    }

    @Override
    public void storeFraudReport(OrderModel order, String pspReference, FraudResult fraudResult) {
        FraudReportModel fraudReport = createFraudReportFromPaymentsResponse(pspReference, fraudResult);
        if (fraudReport != null) {
            fraudReport.setOrder(order);
            storeFraudReport(fraudReport);
        }
    }

    @Override
    public void updatePaymentInfo(OrderModel order, String paymentMethodType, Map<String, String> additionalData) {
        if (order == null) {
            LOG.error("Order is null");
            return;
        }

        PaymentInfoModel paymentInfo = order.getPaymentInfo();
        if(StringUtils.isNotEmpty(paymentMethodType)) {
            paymentInfo.setAdyenPaymentMethod(paymentMethodType);
        }else {
            updatePaymentInfo(paymentInfo, additionalData, "checkout.cardAddedBrand", PaymentInfoModel::setAdyenPaymentMethod);
        }

        updatePaymentInfo(paymentInfo, additionalData, "cardSummary", PaymentInfoModel::setAdyenCardSummary);
        updatePaymentInfo(paymentInfo, additionalData, "authCode", PaymentInfoModel::setAdyenAuthCode);
        updatePaymentInfo(paymentInfo, additionalData, "avsResult", PaymentInfoModel::setAdyenAvsResult);
        updatePaymentInfo(paymentInfo, additionalData, "cardBin", PaymentInfoModel::setAdyenCardBin);
        updatePaymentInfo(paymentInfo, additionalData, "cardHolderName", PaymentInfoModel::setAdyenCardHolder);
        updatePaymentInfo(paymentInfo, additionalData, "expiryDate", (info, value) -> {
            try {
                info.setAdyenCardExpiry(expDateFormatter.parse(value));
            } catch (ParseException e) {
                LOG.warn("Failed to parse expiry date", e);
            }
        });
        updatePaymentInfo(paymentInfo, additionalData, "threeDOffered", (info, value) -> info.setAdyenThreeDOffered(Boolean.valueOf(value)));
        updatePaymentInfo(paymentInfo, additionalData, "threeDAuthenticated", (info, value) -> info.setAdyenThreeDAuthenticated(Boolean.valueOf(value)));
        updatePaymentInfo(paymentInfo, additionalData, "pos.receipt", PaymentInfoModel::setAdyenPosReceipt);

        modelService.save(paymentInfo);
    }

    protected void updatePaymentInfo(PaymentInfoModel paymentInfo, Map<String, String> additionalData, String key, BiConsumer<PaymentInfoModel, String> setter) {
        if (additionalData != null && additionalData.containsKey(key)) {
            setter.accept(paymentInfo, additionalData.get(key));
        }
    }

    public ModelService getModelService() {
        return modelService;
    }

    public void setModelService(ModelService modelService) {
        this.modelService = modelService;
    }

    public void setTransactionTemplate(TransactionOperations transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }
}
