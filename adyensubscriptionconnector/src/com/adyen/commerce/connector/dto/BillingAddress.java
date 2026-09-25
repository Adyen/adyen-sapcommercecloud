/*
 * Copyright (c) 2026 Adyen B.V. - MIT license.
 */
package com.adyen.commerce.connector.dto;

/**
 * Non-payment billing address data supplied to billing-platform token import operations.
 *
 * <p>{@code confirmed} says whether this is the address the shopper gave for the payment method, or whether
 * it was inferred from the order's delivery address because none was captured, as on wallet and APM
 * checkouts. An inferred address is worth sending as an address but is no evidence of who owns the card —
 * on a gift order the delivery name is somebody else — so connectors must not use one to name an account.
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
