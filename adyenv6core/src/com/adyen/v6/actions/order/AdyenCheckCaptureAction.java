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
package com.adyen.v6.actions.order;

import com.adyen.v6.actions.AbstractWaitableAction;
import com.adyen.v6.factory.AdyenPaymentServiceFactory;
import com.adyen.v6.service.AdyenCheckoutApiService;
import com.adyen.v6.service.AdyenTransactionService;
import com.adyen.v6.util.AmountUtil;
import de.hybris.platform.core.enums.OrderStatus;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.orderprocessing.model.OrderProcessModel;
import de.hybris.platform.payment.dto.TransactionStatus;
import de.hybris.platform.payment.dto.TransactionStatusDetails;
import de.hybris.platform.payment.enums.PaymentTransactionType;
import de.hybris.platform.payment.model.PaymentTransactionEntryModel;
import de.hybris.platform.payment.model.PaymentTransactionModel;
import de.hybris.platform.store.services.BaseStoreService;
import org.apache.log4j.Logger;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Check if order is captured
 */
public class AdyenCheckCaptureAction extends AbstractWaitableAction<OrderProcessModel> {
    private static final Logger LOG = Logger.getLogger(AdyenCheckCaptureAction.class);

    private final AdyenPaymentServiceFactory adyenPaymentServiceFactory;
    private final BaseStoreService baseStoreService;

    public AdyenCheckCaptureAction(final AdyenPaymentServiceFactory adyenPaymentServiceFactory,
                                         final BaseStoreService baseStoreService) {
        this.adyenPaymentServiceFactory = adyenPaymentServiceFactory;
        this.baseStoreService = baseStoreService;
    }

    @Override
    public Set<String> getTransitions() {
        return Transition.getStringValues();
    }

    @Override
    public String execute(final OrderProcessModel process) {
        LOG.debug("Process: " + process.getCode() + " in step " + getClass().getSimpleName());

        final OrderModel order = process.getOrder();

        if (order.getPaymentInfo().getAdyenPaymentMethod() == null) {
            LOG.debug("Not Adyen Payment");
            return Transition.OK.toString();
        }

        /*
        TakePaymentAction will move the order to PAYMENT_CAPTURED state
        Revert it back to PAYMENT_NOT_CAPTURED
         */
        order.setStatus(OrderStatus.PAYMENT_NOT_CAPTURED);
        modelService.save(order);

        BigDecimal remainingAmount = AmountUtil.calculateAmountWithTaxes(order);
        for (final PaymentTransactionModel paymentTransactionModel : order.getPaymentTransactions()) {
            boolean isRejected = AdyenTransactionService.getTransactionEntry(
                    paymentTransactionModel,
                    PaymentTransactionType.CAPTURE,
                    TransactionStatus.REJECTED
            ) != null;

            boolean isErroneous = AdyenTransactionService.getTransactionEntry(
                    paymentTransactionModel,
                    PaymentTransactionType.CAPTURE,
                    TransactionStatus.ERROR
            ) != null;

            //Fail if capture is rejected
            if (isErroneous || isRejected) {
                LOG.debug("Process: " + process.getCode() + " Order Not Captured");
                return Transition.NOK.toString();
            }

            PaymentTransactionEntryModel transactionEntry = AdyenTransactionService.getTransactionEntry(
                    paymentTransactionModel,
                    PaymentTransactionType.CAPTURE,
                    TransactionStatus.ACCEPTED,
                    TransactionStatusDetails.SUCCESFULL);

            if (transactionEntry != null) {
                remainingAmount = remainingAmount.subtract(transactionEntry.getAmount());
                LOG.debug("Remaining amount: " + remainingAmount);
            }
        }

        BigDecimal zero = BigDecimal.ZERO;

        //Setting scale to 3, to avoid comparison issues on more than 3 decimal places
        zero = zero.setScale(3, BigDecimal.ROUND_FLOOR);
        remainingAmount = remainingAmount.setScale(3, BigDecimal.ROUND_FLOOR);

        //Return success if all transactions are captured
        if (remainingAmount.compareTo(zero) <= 0) {
            LOG.debug("Process: " + process.getCode() + " Order Captured");
            order.setStatus(OrderStatus.PAYMENT_CAPTURED);
            modelService.save(order);
            return Transition.OK.toString();
        }

        //By default Wait for capture result
        LOG.debug("Process: " + process.getCode() + " Order Waiting");
        return Transition.WAIT.toString();
    }

    public AdyenCheckoutApiService getAdyenPaymentService(final OrderModel orderModel) {
        return adyenPaymentServiceFactory.createAdyenCheckoutApiService(orderModel.getStore());
    }
}
