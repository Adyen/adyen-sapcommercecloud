/*
 * Copyright (c) 2026 Adyen B.V. - MIT license.
 */
package com.adyen.commerce.connector.dto;

/**
 * Non-payment billing address data supplied to billing-platform token import operations.
 */
public record BillingAddress(String firstName,
                             String lastName,
                             String street1,
                             String street2,
                             String city,
                             String region,
                             String postalCode,
                             String country,
                             String phone)
{
}
