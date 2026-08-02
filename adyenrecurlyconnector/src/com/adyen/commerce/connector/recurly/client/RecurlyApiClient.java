package com.adyen.commerce.connector.recurly.client;

import java.util.List;

import com.adyen.commerce.connector.dto.BillingAddress;
import com.adyen.commerce.connector.dto.CardMetadata;
import com.adyen.commerce.connector.exception.BillingException;

public interface RecurlyApiClient
{
    /**
     * Resolves the stable Recurly account id. Wallet mode creates a missing account immediately; primary-billing-info
     * mode defers creation until token import so the account and its external Adyen token can be created atomically.
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
