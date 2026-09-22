package com.adyen.commerce.connector.reconciliation;

import com.adyen.commerce.connector.dto.NormalizedSubscription;
import com.adyen.commerce.connector.exception.BillingException;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;

/**
 * Re-fetches and applies the billing platform's authoritative subscription snapshot.
 */
public interface SubscriptionReconciliationService
{
	NormalizedSubscription reconcile(BillingSubscriptionRefModel subscription) throws BillingException;
}
