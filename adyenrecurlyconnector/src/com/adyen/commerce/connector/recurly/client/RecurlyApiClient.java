package com.adyen.commerce.connector.recurly.client;

import java.util.List;

import com.adyen.commerce.connector.dto.BillingAddress;
import com.adyen.commerce.connector.dto.CardMetadata;
import com.adyen.commerce.connector.dto.NormalizedSubscription;
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

    /**
     * Stops the subscription renewing and lets it serve out the period the customer has paid for
     * ({@code PUT /subscriptions/{id}/cancel} with {@code timeframe=bill_date}).
     *
     * <p>Separate from {@link #terminate} rather than the two sharing one method and a flag, because Recurly
     * draws the line between them at the HTTP verb: this one is reversible until the term ends, and the
     * other one is not. A boolean argument would have made the destructive call look like a value.</p>
     */
    void cancelAtNextBillDate(String subscriptionId, String idempotencyKey) throws BillingException;

    /**
     * Ends the subscription immediately, forfeiting the remainder of the paid period
     * ({@code DELETE /subscriptions/{id}}).
     *
     * <p>Recurly's own word for this is <em>terminate</em>, and it is not a cancellation in a hurry: it
     * stops service at once and settles the unused period according to the {@code refund} parameter, which
     * this client does not send — so the account's own default decides what happens to the customer's
     * money. Reserve it for operator and system decisions.</p>
     */
    void terminate(String subscriptionId, String idempotencyKey) throws BillingException;

    /**
     * Resolves subscription UUIDs for lightweight Recurly invoice/payment JSON webhooks.
     */
    List<String> resolveWebhookSubscriptionIds(String resourceType, String resourceId) throws BillingException;

    NormalizedSubscription fetchSubscription(String subscriptionId)
            throws BillingException;
}