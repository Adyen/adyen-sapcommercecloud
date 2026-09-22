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

import org.apache.log4j.Logger;
import de.hybris.platform.core.enums.OrderStatus;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.orderprocessing.model.OrderProcessModel;
import de.hybris.platform.payment.PaymentService;
import de.hybris.platform.payment.dto.TransactionStatus;
import de.hybris.platform.payment.model.PaymentTransactionEntryModel;
import de.hybris.platform.payment.model.PaymentTransactionModel;
import de.hybris.platform.processengine.action.AbstractSimpleDecisionAction;

/**
 * Overrides Hybris TakePaymentAction to remove the txn.getInfo() instanceof CreditCardPaymentInfoModel check
 * The TakePayment step captures the payment transaction.
 */
public class AdyenTakePaymentAction extends AbstractSimpleDecisionAction<OrderProcessModel> {
    private static final Logger LOG = Logger.getLogger(AdyenTakePaymentAction.class);

    private PaymentService paymentService;

    @Override
    public Transition executeAction(final OrderProcessModel process) {
        final OrderModel order = process.getOrder();

        for (final PaymentTransactionModel txn : order.getPaymentTransactions()) {
            final PaymentTransactionEntryModel txnEntry = getPaymentService().capture(txn);

            if (TransactionStatus.ACCEPTED.name().equals(txnEntry.getTransactionStatus())) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("The payment transaction has been captured. Order: " + order.getCode() + ". Txn: " + txn.getCode());
                }
                setOrderStatus(order, OrderStatus.PAYMENT_CAPTURED);
            } else {
                LOG.error("The payment transaction capture has failed. Order: " + order.getCode() + ". Txn: " + txn.getCode());
                setOrderStatus(order, OrderStatus.PAYMENT_NOT_CAPTURED);
                return Transition.NOK;
            }
        }
        return Transition.OK;
    }

    protected PaymentService getPaymentService() {
        return paymentService;
    }

    public void setPaymentService(final PaymentService paymentService) {
        this.paymentService = paymentService;
    }
}