package com.adyen.commerce.connector.recurly.client;

import java.util.List;

import com.adyen.commerce.connector.dto.BillingAddress;
import com.adyen.commerce.connector.dto.CardMetadata;
import com.adyen.commerce.connector.dto.PlatformPaymentMethod;
import com.adyen.commerce.connector.dto.NormalizedSubscription;
import com.adyen.commerce.connector.exception.BillingException;

public interface RecurlyApiClient {
    /** Creates or finds the Recurly account and syncs the customer profile. Runs before any token import. */
    String ensureCustomer(String customerId, String email, String firstName, String lastName) throws BillingException;

    /**
     * Adds or reuses the Adyen gateway reference and returns its billing-info id: the account's primary billing
     * info, or a Wallet entry when Wallet is enabled.
     */
    String importAdyenToken(String accountId, String shopperReference, String storedPaymentMethodId, CardMetadata card,
                            String networkTransactionId, BillingAddress billingAddress) throws BillingException;

    String createSubscription(RecurlySubscriptionParams params) throws BillingException;

    /**
     * All billing infos of the account, newest first, including those not imported by this integration
     * (hosted pages, support, Account Updater): the account is the ownership boundary.
     */
    List<PlatformPaymentMethod> listBillingInfos(String accountId) throws BillingException;

    /**
     * Recurly's hosted account management URL, or {@code null} without a hosted login token. The URL signs the
     * shopper in, so it is a credential: never log or store it.
     */
    String hostedAccountManagementUrl(String accountId) throws BillingException;

    /** Points a subscription at one of the account's billing infos. Wallet only; affects future billing only. */
    void assignBillingInfo(String subscriptionId, String billingInfoId, String idempotencyKey)
            throws BillingException;

    /** Makes the billing info the account's primary one, which moves every subscription not pinned to another. */
    void promoteBillingInfoToPrimary(String accountId, String billingInfoId, String idempotencyKey)
            throws BillingException;

    void updateSubscription(String subscriptionId, String planCode, Integer quantity, String idempotencyKey)
            throws BillingException;

    /** Stops renewal and lets the paid period run out ({@code timeframe=bill_date}). Reversible until term end. */
    void cancelAtNextBillDate(String subscriptionId, String idempotencyKey) throws BillingException;

    /**
     * Ends the subscription immediately. No {@code refund} parameter is sent, so the Recurly account default
     * settles the unused period.
     */
    void terminate(String subscriptionId, String idempotencyKey) throws BillingException;

    /** Subscription UUIDs referenced by an invoice or payment webhook. */
    List<String> resolveWebhookSubscriptionIds(String resourceType, String resourceId) throws BillingException;

    NormalizedSubscription fetchSubscription(String subscriptionId)
            throws BillingException;
}
