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
package com.adyen.v6.commands;

import com.adyen.model.checkout.PaymentRefundResponse;
import com.adyen.v6.factory.AdyenPaymentServiceFactory;
import com.adyen.v6.repository.BaseStoreRepository;
import com.adyen.v6.service.AdyenModificationsApiService;
import de.hybris.platform.payment.commands.FollowOnRefundCommand;
import de.hybris.platform.payment.commands.request.FollowOnRefundRequest;
import de.hybris.platform.payment.commands.result.RefundResult;
import de.hybris.platform.store.BaseStoreModel;
import org.apache.log4j.Logger;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Date;

import static de.hybris.platform.payment.dto.TransactionStatus.ACCEPTED;
import static de.hybris.platform.payment.dto.TransactionStatus.ERROR;
import static de.hybris.platform.payment.dto.TransactionStatusDetails.REVIEW_NEEDED;
import static de.hybris.platform.payment.dto.TransactionStatusDetails.UNKNOWN_CODE;

/**
 * Issues a Refund Request
 */
public class AdyenFollowOnRefundCommand implements FollowOnRefundCommand<FollowOnRefundRequest> {
    private static final Logger LOG = Logger.getLogger(AdyenFollowOnRefundCommand.class);

    private final String REFUND_RECEIVED_RESPONSE = "[refund-received]";

    private AdyenPaymentServiceFactory adyenPaymentServiceFactory;
    private BaseStoreRepository baseStoreRepository;

    @Override
    public RefundResult perform(FollowOnRefundRequest request) {
        RefundResult result = new RefundResult();
        result.setCurrency(request.getCurrency());
        result.setTotalAmount(request.getTotalAmount());
        result.setRequestTime(new Date());

        //Assign error status by default
        result.setTransactionStatus(ERROR);
        result.setTransactionStatusDetails(UNKNOWN_CODE);

        LOG.info("Refund request received with requestId: " + request.getRequestId() + ", requestToken: " + request.getRequestToken());

        String originalPSPReference = request.getRequestId();
        String reference = request.getRequestToken();
        final BigDecimal amount = request.getTotalAmount();
        final Currency currency = request.getCurrency();

        BaseStoreModel baseStore = baseStoreRepository.findByOrder(reference);
        if (baseStore == null) {
            return result;
        }
        AdyenModificationsApiService adyenModificationsApiService = adyenPaymentServiceFactory.createAdyenModificationsApiService(baseStore);

        try {
            //Do the /refund API call
            PaymentRefundResponse paymentRefundResponse = adyenModificationsApiService.refund(amount, currency, originalPSPReference, reference);

            LOG.debug("Refund response: " + paymentRefundResponse.toString());
            //change status to ACCEPTED if there is no error
            if (PaymentRefundResponse.StatusEnum.RECEIVED.equals(paymentRefundResponse.getStatus())) {
                result.setTransactionStatus(ACCEPTED);
                result.setTransactionStatusDetails(REVIEW_NEEDED);
            }
        } catch (Exception e) {
            LOG.error("Refund Exception", e);
        }

        LOG.info("Refund status: " + result.getTransactionStatus().name() + ":" + result.getTransactionStatusDetails().name());

        return result;
    }

    public AdyenPaymentServiceFactory getAdyenPaymentServiceFactory() {
        return adyenPaymentServiceFactory;
    }

    public void setAdyenPaymentServiceFactory(AdyenPaymentServiceFactory adyenPaymentServiceFactory) {
        this.adyenPaymentServiceFactory = adyenPaymentServiceFactory;
    }

    public BaseStoreRepository getBaseStoreRepository() {
        return baseStoreRepository;
    }

    public void setBaseStoreRepository(BaseStoreRepository baseStoreRepository) {
        this.baseStoreRepository = baseStoreRepository;
    }
}
