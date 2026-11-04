package com.adyen.v6.exceptions;

/**
 * The shopper's stored cards could not be read from Adyen: the call failed, as opposed to succeeding with an
 * empty list. Callers that act on the answer (for example by checking that a card belongs to the shopper)
 * need the difference, since "no cards" and "could not ask" lead to different decisions.
 */
public class AdyenStoredCardsUnavailableException extends Exception {
    public AdyenStoredCardsUnavailableException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
