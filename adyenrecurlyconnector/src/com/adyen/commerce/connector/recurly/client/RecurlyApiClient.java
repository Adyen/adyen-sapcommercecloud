package com.adyen.commerce.connector.recurly.client;

import java.util.List;

import com.adyen.commerce.connector.dto.BillingAddress;
import com.adyen.commerce.connector.dto.CardMetadata;
import com.adyen.commerce.connector.exception.BillingException;

public interface RecurlyApiClient {
    /**
     * Creates or resolves the stable Recurly account id and synchronizes the customer profile. Account creation is
     * performed before token import in both Wallet and primary-billing-info modes so customer data is not lost.
     */
    String ensureCustomer(String customerId, String email, String firstName, String lastName) throws BillingException;

    /**
     * Adds or reuses the exact Adyen gateway reference and returns its Recurly billing-info id. Depending on the
     * configured mode, the token is either the account's single primary billing info or a Wallet billing info.
     */
    String importAdyenToken(String accountId, String shopperReference, String storedPaymentMethodId, CardMetadata card,
                            BillingAddress billingAddress) throws BillingException;

    String createSubscription(RecurlySubscriptionParams params) throws BillingException;

    void updateSubscription(String subscriptionId, String planCode, Integer quantity, String idempotencyKey)
            throws BillingException;

    void cancelSubscription(String subscriptionId, boolean atPeriodEnd, String idempotencyKey) throws BillingException;

    /**
     * Resolves subscription UUIDs for lightweight Recurly invoice/payment JSON webhooks.
     */
    List<String> resolveWebhookSubscriptionIds(String resourceType, String resourceId) throws BillingException;
}