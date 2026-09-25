package com.adyen.commerce.connector.recurly.client;

import java.util.Map;

/**
 * Subscription create parameters in Recurly terms. {@code startsAt} is ISO-8601 and future-dated, as the
 * Adyen gateway-token import requires.
 */
public record RecurlySubscriptionParams(String accountId,
                                        String billingInfoId,
                                        String planCode,
                                        int quantity,
                                        String currencyIsoCode,
                                        String startsAt,
                                        String networkTransactionId,
                                        String subscriptionId,
                                        Map<String, String> metadata) {
}
