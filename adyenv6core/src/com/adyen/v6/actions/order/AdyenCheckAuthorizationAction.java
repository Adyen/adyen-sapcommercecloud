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
import com.adyen.v6.util.AmountUtil;
import de.hybris.platform.core.enums.OrderStatus;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.order.payment.PaymentInfoModel;
import de.hybris.platform.orderprocessing.model.OrderProcessModel;
import de.hybris.platform.payment.dto.TransactionStatus;
import de.hybris.platform.payment.enums.PaymentTransactionType;
import de.hybris.platform.payment.model.PaymentTransactionEntryModel;
import de.hybris.platform.payment.model.PaymentTransactionModel;
import de.hybris.platform.store.services.BaseStoreService;
import org.apache.log4j.Logger;

import java.math.BigDecimal;

/**
 * Check if order is authorized
 */
public class AdyenCheckAuthorizationAction extends AbstractWaitableAction<OrderProcessModel> {
    private static final Logger LOG = Logger.getLogger(AdyenCheckAuthorizationAction.class);

    private final AdyenPaymentServiceFactory adyenPaymentServiceFactory;
    private final BaseStoreService baseStoreService;

    public AdyenCheckAuthorizationAction(final AdyenPaymentServiceFactory adyenPaymentServiceFactory,
                                         final BaseStoreService baseStoreService) {
        this.adyenPaymentServiceFactory = adyenPaymentServiceFactory;
        this.baseStoreService = baseStoreService;
    }


    @Override
    public String execute(final OrderProcessModel process) {
        LOG.debug("Process: " + process.getCode() + " in step " + getClass().getSimpleName());

        final OrderModel order = process.getOrder();

        //Fail when no order
        if (order == null) {
            LOG.error("Order is null!");
            return Transition.NOK.toString();
        }
        final PaymentInfoModel paymentInfo = order.getPaymentInfo();

        //Continue if it's not Adyen payment
        if (paymentInfo.getAdyenPaymentMethod() == null || paymentInfo.getAdyenPaymentMethod().isEmpty()) {
            LOG.debug("Not Adyen Payment");
            return Transition.OK.toString();
        }

        return processOrderAuthorization(process, order);
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

    protected String processOrderAuthorization(final OrderProcessModel process, final OrderModel order) {
        //No transactions means that is not authorized yet
        if (order.getPaymentTransactions() == null || order.getPaymentTransactions().isEmpty()) {
            LOG.debug("Process: " + process.getCode() + " Order Waiting");
            return Transition.WAIT.toString();
        }
        
        BigDecimal remainingAmount = AmountUtil.calculateAmountWithTaxes(order);
        for (final PaymentTransactionModel paymentTransactionModel : order.getPaymentTransactions()) {
            if (!isTransactionAuthorized(paymentTransactionModel)) {
                //A single not authorized transaction means not authorized
                LOG.debug("Process: " + process.getCode() + " Order Not Authorized");
                order.setStatus(OrderStatus.PAYMENT_NOT_AUTHORIZED);
                modelService.save(order);
                return Transition.NOK.toString();
            }
            
            remainingAmount = remainingAmount.subtract(paymentTransactionModel.getPlannedAmount());
        }

        BigDecimal zero = BigDecimal.ZERO;
        //Setting scale to 3, to avoid comparison issues on more than 3 decimal places
        zero = zero.setScale(3, BigDecimal.ROUND_FLOOR);
        remainingAmount = remainingAmount.setScale(3, BigDecimal.ROUND_FLOOR);

        //Wait if there is still amount to be authorized
        if (remainingAmount.compareTo(zero) > 0) {
            LOG.debug("Process: " + process.getCode() + " Order Waiting remaining amount to be authorized");
            return Transition.WAIT.toString();
        }

        //Return success if all transactions and total amount are authorised
        LOG.debug("Process: " + process.getCode() + " Order Authorized");
        order.setStatus(OrderStatus.PAYMENT_AUTHORIZED);
        modelService.save(order);

        return Transition.OK.toString();
    }
}
