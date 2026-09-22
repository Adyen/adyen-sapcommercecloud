package com.adyen.commerce.connector.recurly;

import org.apache.commons.lang3.StringUtils;

import com.adyen.commerce.connector.exception.PreconditionFailedException;

/**
 * Recurly needs both the imported billing-info id and the original Adyen NTID when the subscription is
 * created. The current vendor-neutral SPI only carries NTID through token import, so the Recurly adapter
 * keeps the two Recurly-specific values together in the payment-method external id.
 */
final class RecurlyPaymentMethodReference {
    private static final String SEPARATOR = "::ntid::";

    private final String billingInfoId;
    private final String networkTransactionId;

    private RecurlyPaymentMethodReference(final String billingInfoId, final String networkTransactionId) {
        this.billingInfoId = billingInfoId;
        this.networkTransactionId = networkTransactionId;
    }

    static String encode(final String billingInfoId, final String networkTransactionId) {
        return billingInfoId + SEPARATOR + networkTransactionId;
    }

    /**
     * The billing info id alone, whichever shape the reference is in. Activation writes the packed form
     * because Recurly wants the NTID when a subscription is created; a repoint writes the bare id, since
     * assigning one needs nothing else. Both name the same billing info, and neither is an error here.
     */
    static String billingInfoIdOf(final String externalId) {
        final String value = StringUtils.defaultString(externalId);
        final int separator = value.indexOf(SEPARATOR);
        return separator <= 0 ? value : value.substring(0, separator);
    }

    static RecurlyPaymentMethodReference parse(final String externalId) throws PreconditionFailedException {
        final String value = StringUtils.defaultString(externalId);
        final int separator = value.indexOf(SEPARATOR);
        if (separator <= 0 || separator + SEPARATOR.length() >= value.length()) {
            throw new PreconditionFailedException(
                    "Recurly payment method reference is missing billing info id or NTID");
        }
        return new RecurlyPaymentMethodReference(value.substring(0, separator),
                value.substring(separator + SEPARATOR.length()));
    }

    String billingInfoId() {
        return billingInfoId;
    }

    String networkTransactionId() {
        return networkTransactionId;
    }
}
