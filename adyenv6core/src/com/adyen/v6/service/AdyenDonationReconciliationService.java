package com.adyen.v6.service;

import com.adyen.v6.model.AdyenNotificationModel;

public interface AdyenDonationReconciliationService {
    void reconcile(AdyenNotificationModel notification);
}
