/*
 * Copyright (c) 2026 Adyen B.V. - MIT license.
 */
package com.adyen.commerce.connector.dto;

/**
 * Non-payment billing address data supplied to billing-platform token import operations.
 *
 * <p>{@code confirmed} says whether this really is the address the shopper gave for the payment method,
 * or whether it was inferred from the order's delivery address because no payment address was captured
 * — which happens on wallet and APM checkouts. An inferred address is still worth sending as an address,
 * but it is not evidence of who owns the card: on a gift order the delivery name is somebody else
 * entirely. Connectors must not use an unconfirmed address to name an account.
 */
public record BillingAddress(String firstName,
                             String lastName,
                             String street1,
                             String street2,
                             String city,
                             String region,
                             String postalCode,
                             String country,
                             String phone,
                             boolean confirmed)
{
}
