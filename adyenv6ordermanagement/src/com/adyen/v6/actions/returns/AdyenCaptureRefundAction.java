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
package com.adyen.v6.actions.returns;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.log4j.Logger;
import com.adyen.v6.actions.AbstractWaitableAction;
import de.hybris.platform.basecommerce.enums.ReturnStatus;
import de.hybris.platform.payment.AdapterException;
import de.hybris.platform.payment.PaymentService;
import de.hybris.platform.payment.dto.TransactionStatus;
import de.hybris.platform.payment.dto.TransactionStatusDetails;
import de.hybris.platform.payment.enums.PaymentTransactionType;
import de.hybris.platform.payment.model.PaymentTransactionEntryModel;
import de.hybris.platform.payment.model.PaymentTransactionModel;
import de.hybris.platform.returns.model.ReturnProcessModel;
import de.hybris.platform.returns.model.ReturnRequestModel;
import de.hybris.platform.warehousing.returns.service.RefundAmountCalculationService;
import org.springframework.retry.annotation.Recover;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Refund step of the return-process
 */
public class AdyenCaptureRefundAction extends AbstractWaitableAction<ReturnProcessModel> {
    private static final Logger LOG = Logger.getLogger(AdyenCaptureRefundAction.class);

    private PaymentService paymentService;
    private RefundAmountCalculationService refundAmountCalculationService;
    private TransactionOperations transactionTemplate;

    @Override
    public String execute(final ReturnProcessModel process) {
        LOG.debug("Process: " + process.getCode() + " in step " + getClass().getSimpleName());

        final ReturnRequestModel returnRequest = process.getReturnRequest();
        final List<PaymentTransactionModel> transactions = returnRequest.getOrder().getPaymentTransactions();

        if (transactions.isEmpty()) {
            LOG.warn("Unable to refund for ReturnRequest " + returnRequest.getCode() + ", no PaymentTransactions found");
            return fail(returnRequest);
        }

        //This assumes that the Order only has one PaymentTransaction
        final PaymentTransactionModel transaction = transactions.get(0);

        final BigDecimal customRefundAmount = refundAmountCalculationService.getCustomRefundAmount(returnRequest);
        BigDecimal amountToRefund = null;

        if (customRefundAmount != null && customRefundAmount.compareTo(BigDecimal.ZERO) > 0) {
            amountToRefund = customRefundAmount;
        } else {
            amountToRefund = refundAmountCalculationService.getOriginalRefundAmount(returnRequest);
        }
        LOG.debug("Amount to refund " + amountToRefund);

        //Find all entries of type REFUND_FOLLOW_ON
        List<PaymentTransactionEntryModel> refundTransactionEntries = transaction.getEntries().stream()
                .filter(entry -> PaymentTransactionType.REFUND_FOLLOW_ON.equals(entry.getType()))
                .collect(Collectors.toList());

        if (refundTransactionEntries.isEmpty()) {
            LOG.warn("No REFUND TXs found");
            try {
                //Send the refund API request
                PaymentTransactionEntryModel transactionEntry = getPaymentService().refundFollowOn(transaction, amountToRefund);

                LOG.info("Refund request status: " + transactionEntry.getTransactionStatus());
                //Wait if it is accepted
                if (TransactionStatus.ACCEPTED.name().equals(transactionEntry.getTransactionStatus())) {
                    return Transition.WAIT.name();
                }
            } catch (final AdapterException e)  //NOSONAR
            {
                LOG.info("Unable to refund for ReturnRequest " + returnRequest.getCode() + ", exception ocurred: " + e.getMessage());
            }

            //Fail when there is an error sending the refund request
            return fail(returnRequest);
        }

        //Find erroneous refund transactions
        boolean hasError = refundTransactionEntries.stream().anyMatch(
                entry -> TransactionStatus.REJECTED.name().equals(entry.getTransactionStatus())
                        || TransactionStatus.ERROR.name().equals(entry.getTransactionStatus())
        );

        if (hasError) {
            LOG.warn("Found failed REFUND transaction for ReturnRequest " + returnRequest.getCode());
            return fail(returnRequest);
        }

        //Calculate already refunded amounts from transactions with ACCEPTED-SUCCESFULL status
        BigDecimal refundedAmount = refundTransactionEntries.stream()
                .filter(
                        entry -> TransactionStatus.ACCEPTED.name().equals(entry.getTransactionStatus())
                                && TransactionStatusDetails.SUCCESFULL.name().equals(entry.getTransactionStatusDetails())
                )
                .map(entry -> entry.getAmount())
                .reduce(new BigDecimal(0), (x, y) -> x.add(y));

        //Lower the scale to avoid comparison issues
        refundedAmount = refundedAmount.setScale(3, BigDecimal.ROUND_FLOOR);
        amountToRefund = amountToRefund.setScale(3, BigDecimal.ROUND_FLOOR);

        LOG.debug("Refunded amount " + refundedAmount);

        //Wait if there is still remaining amount to be refunded
        if (refundedAmount.compareTo(amountToRefund) < 0) {
            LOG.debug("Waiting for the remaining amount");
            return Transition.WAIT.name();
        }

        return success(returnRequest);
    }

    private String fail(ReturnRequestModel returnRequest) {
        setReturnRequestStatus(returnRequest, ReturnStatus.PAYMENT_REVERSAL_FAILED);
        return Transition.NOK.name();
    }

    private String success(ReturnRequestModel returnRequest) {
        setReturnRequestStatus(returnRequest, ReturnStatus.PAYMENT_REVERSED);
        return Transition.OK.name();
    }

    /**
     * Update the return status for all return entries in {@link ReturnRequestModel}
     *
     * @param returnRequest - the return request
     * @param status        - the return status
     */
    protected void setReturnRequestStatus(final ReturnRequestModel returnRequest, final ReturnStatus status) {
        transactionTemplate.execute(transactionStatus -> {
            returnRequest.setStatus(status);
            returnRequest.getReturnEntries().stream().forEach(entry -> {
                entry.setStatus(status);
                getModelService().save(entry);
            });
            getModelService().save(returnRequest);
            return null;
        });
    }

    protected PaymentService getPaymentService() {
        return paymentService;
    }


    public void setPaymentService(final PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    protected RefundAmountCalculationService getRefundAmountCalculationService() {
        return refundAmountCalculationService;
    }


    public void setRefundAmountCalculationService(RefundAmountCalculationService refundAmountCalculationService) {
        this.refundAmountCalculationService = refundAmountCalculationService;
    }

    public void setTransactionTemplate(TransactionOperations transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }
}
