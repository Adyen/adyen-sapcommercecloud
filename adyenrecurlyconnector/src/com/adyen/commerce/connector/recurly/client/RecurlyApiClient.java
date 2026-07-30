package com.adyen.commerce.connector.recurly.client;

import com.adyen.commerce.connector.dto.BillingAddress;
import com.adyen.commerce.connector.dto.CardMetadata;
import com.adyen.commerce.connector.exception.BillingException;

public interface RecurlyApiClient
{
    /**
     * Resolves the stable Recurly account identifier. Creation of a missing account is deferred until
     * {@link #importAdyenToken(String, String, String, CardMetadata, BillingAddress)} so external
     * gateway references can be included in the account-creation request.
     */
    String ensureCustomer(String customerId, String email, String firstName, String lastName) throws BillingException;

    String importAdyenToken(String accountId, String shopperReference, String storedPaymentMethodId, CardMetadata card,
                            BillingAddress billingAddress) throws BillingException;

    String createSubscription(RecurlySubscriptionParams params) throws BillingException;

    void updateSubscription(String subscriptionId, String planCode, Integer quantity, String idempotencyKey)
            throws BillingException;

    void cancelSubscription(String subscriptionId, boolean atPeriodEnd, String idempotencyKey) throws BillingException;
}
