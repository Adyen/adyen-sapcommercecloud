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
import com.adyen.v6.model.AdyenNotificationModel;
import com.adyen.v6.model.NotificationItemModel;
import de.hybris.platform.payment.model.PaymentTransactionEntryModel;
import de.hybris.platform.payment.model.PaymentTransactionModel;

import java.io.IOException;

/**
 * Service for Adyen notifications manipulation
 */
public interface AdyenNotificationService {

    /**
     * Process NotificationItemModel
     * Handles multiple eventCodes
     */
    void processNotification(NotificationItemModel notificationItemModel);

    /**
     * Save NotificationItemModels
     */
    void saveNotifications(NotificationRequest notificationRequest);

    /**
     * Parse HTTP request body and return NotificationRequest
     */
    NotificationRequest getNotificationRequestFromString(String requestString) throws IOException;

    /**
     * Create NotificationItemModel from NotificationRequestItem
     */
    NotificationItemModel createFromNotificationRequest(NotificationRequestItem notificationRequestItem);

    AdyenNotificationModel createNotificationInfoModel(NotificationRequestItem notificationRequestItem);
    /**
     * Save NotificationItemModel from NotificationRequestItem
     */
    void saveFromNotificationRequest(NotificationRequestItem notificationRequestItem);

    /**
     * Process notification with eventCode=CAPTURED
     */
    PaymentTransactionEntryModel processCapturedEvent(AdyenNotificationModel notificationItemModel, PaymentTransactionModel paymentTransactionModel);

    /**
     * Process notification with eventCode=AUTHORISED
     */
    PaymentTransactionModel processAuthorisationEvent(AdyenNotificationModel notificationItemModel);

    /**
     * Process notification with eventCode=CANCEL_OR_REFUND
     */
    PaymentTransactionEntryModel processCancelEvent(AdyenNotificationModel notificationItemModel, PaymentTransactionModel paymentTransactionModel);

    /**
     * Process notification with eventCode=REFUND
     */
    PaymentTransactionEntryModel processRefundEvent(AdyenNotificationModel notificationItem);

    /**
     * Process notification with eventCode=OFFER_CLOSED
     * @return
     */
    PaymentTransactionModel processOfferClosedEvent(AdyenNotificationModel notificationItem);

}
