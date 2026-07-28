package com.adyen.commerce.connector.recurly.client;

import java.util.Map;

/**
 * Vendor-facing subscription create parameters after the SPI request has been normalized into Recurly
 * fields. {@code startsAt} is an ISO-8601 timestamp because Recurly's Adyen gateway-token import flow
 * must be future-dated in this adapter.
 */
public record RecurlySubscriptionParams(String accountId,
                                        String billingInfoId,
                                        String planCode,
                                        int quantity,
                                        String currencyIsoCode,
                                        String startsAt,
                                        String networkTransactionId,
                                        String subscriptionId,
                                        Map<String, String> metadata)
{
}
