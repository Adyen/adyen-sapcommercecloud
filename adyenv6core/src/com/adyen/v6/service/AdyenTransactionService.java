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
import com.adyen.v6.model.AdyenNotificationModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.payment.dto.TransactionStatus;
import de.hybris.platform.payment.dto.TransactionStatusDetails;
import de.hybris.platform.payment.enums.PaymentTransactionType;
import de.hybris.platform.payment.model.PaymentTransactionEntryModel;
import de.hybris.platform.payment.model.PaymentTransactionModel;

import java.math.BigDecimal;

public interface AdyenTransactionService {
    /**
     * Get TX entry by type and status
     */
    static PaymentTransactionEntryModel getTransactionEntry(PaymentTransactionModel paymentTransactionModel, PaymentTransactionType paymentTransactionType, TransactionStatus transactionStatus) {
        return paymentTransactionModel.getEntries().stream()
                .filter(
                        entry -> paymentTransactionType.equals(entry.getType())
                                && transactionStatus.name().equals(entry.getTransactionStatus())
                )
                .findFirst()
                .orElse(null);
    }

    /**
     * Get TX entry by type, status and details
     */
    static PaymentTransactionEntryModel getTransactionEntry(PaymentTransactionModel paymentTransactionModel,
                                                            PaymentTransactionType paymentTransactionType,
                                                            TransactionStatus transactionStatus,
                                                            TransactionStatusDetails transactionStatusDetails) {
        return paymentTransactionModel.getEntries().stream()
                .filter(
                        entry -> paymentTransactionType.equals(entry.getType())
                                && transactionStatus.name().equals(entry.getTransactionStatus())
                                && transactionStatusDetails.name().equals(entry.getTransactionStatusDetails())
                )
                .findFirst()
                .orElse(null);
    }

    /**
     * Creates a PaymentTransactionEntryModel with type=CAPTURE from NotificationItemModel
     */
    PaymentTransactionEntryModel createCapturedTransactionFromNotification(PaymentTransactionModel paymentTransaction, AdyenNotificationModel notificationItemModel);

    /**
     * Creates a PaymentTransactionEntryModel with type=REFUND_FOLLOW_ON from NotificationItemModel
     */
    PaymentTransactionEntryModel createRefundedTransactionFromNotification(PaymentTransactionModel paymentTransaction, AdyenNotificationModel notificationItemModel);

    /**
     * Stores the authorization transactions for an order
     */
    PaymentTransactionModel authorizeOrderModel(AbstractOrderModel abstractOrderModel, String merchantTransactionCode, String pspReference);

    /**
     * Store failed authorization transaction entry
     */
    PaymentTransactionModel storeFailedAuthorizationFromNotification(AdyenNotificationModel notificationItemModel, AbstractOrderModel abstractOrderModel);

    /**
     * Creates a PaymentTransactionEntryModel with type=CANCEL
     */
    PaymentTransactionEntryModel createCancellationTransaction(PaymentTransactionModel paymentTransaction, String merchantCode, String pspReference);

    /**
     * Creates a PaymentTransactionModel
     */
    PaymentTransactionModel createPaymentTransactionFromResultCode(AbstractOrderModel abstractOrderModel, String merchantTransactionCode, String pspReference, PaymentDetailsResponse.ResultCodeEnum resultCodeEnum);

    /**
     * Stores the authorization transactions for an order
     */
    PaymentTransactionModel authorizeOrderModel(AbstractOrderModel abstractOrderModel, String merchantTransactionCode, String pspReference, BigDecimal paymentAmount);
}
