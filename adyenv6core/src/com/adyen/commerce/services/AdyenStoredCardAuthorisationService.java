package com.adyen.commerce.services;

import java.util.Map;

import com.adyen.model.checkout.PaymentResponse;

import de.hybris.platform.core.model.user.CustomerModel;

/**
 * Keeps the network transaction id of the authorisation that vaulted a shopper's card.
 *
 * <p>Adyen reports it once, in the authorisation response's {@code additionalData}, and does not repeat it
 * when the vaulted tokens are listed. A billing platform that charges an imported token as a
 * merchant-initiated transaction needs it, so a card vaulted outside any order has to have it captured at
 * the moment it is authorised or it cannot be recovered at all.</p>
 */
public interface AdyenStoredCardAuthorisationService
{
    /**
     * Record what an authorisation produced, if it produced both a token and a reference. A response
     * carrying neither is ignored rather than rejected: not every payment method yields them, and a card
     * that simply cannot be used for a merchant-initiated charge is not an error here.
     */
    void recordFrom(CustomerModel customer, String merchantAccount, PaymentResponse response);

    /**
     * Every network transaction id known for this shopper's vaulted cards, keyed by stored payment method
     * id. Includes the ones orders captured on their own PaymentInfo, so a card the shopper has actually
     * paid with is usable without having been re-authorised here.
     */
    Map<String, String> networkTxReferencesFor(CustomerModel customer);
}
