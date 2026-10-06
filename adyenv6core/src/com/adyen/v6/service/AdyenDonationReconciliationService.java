package com.adyen.v6.service;

import com.adyen.v6.model.AdyenNotificationModel;

/**
 * Reconciles an authenticated Adyen DONATION webhook with its commerce order.
 */
public interface AdyenDonationReconciliationService {
    void reconcile(AdyenNotificationModel notification);
}
