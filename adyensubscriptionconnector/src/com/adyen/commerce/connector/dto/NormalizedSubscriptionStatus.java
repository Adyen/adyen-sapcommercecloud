package com.adyen.commerce.connector.dto;

/**
 * Subscription lifecycle states understood by the connector core.
 *
 * <p>The vocabulary is normalized on what the subscription <em>does for the customer</em>, not on the word
 * the platform happens to use, because a consumer asking "is this still serving?" or "has this ended?" must
 * not have to know which platform answered. Two platform states that read as opposites illustrate it:
 * Recurly's {@code canceled} and Chargebee's {@code non_renewing} both keep serving until the term ends, so
 * both are {@link #ACTIVE} carrying {@code cancelAtPeriodEnd}; Recurly's {@code expired} and Chargebee's
 * {@code cancelled} have both stopped, so both are {@link #EXPIRED}.
 */
public enum NormalizedSubscriptionStatus
{
	PENDING,
	ACTIVE,
	PAST_DUE,
	PAUSED,

	/**
	 * Ended by an explicit cancellation that took effect immediately, as distinct from {@link #EXPIRED}.
	 *
	 * <p><strong>Neither shipped adapter produces this.</strong> Recurly and Chargebee both collapse the two
	 * endings into one state, so for them "ended" is always {@link #EXPIRED}. It is kept for a platform that
	 * really does distinguish them, and because rows written before the vocabulary settled may still carry
	 * it — the reconciliation sweep therefore still treats it as terminal. Anything reading status to decide
	 * whether a subscription has ended should test for {@link #EXPIRED} <em>and</em> this, not either alone.
	 */
	CANCELLED,

	/** Ended: the subscription no longer serves the customer, whatever the platform calls that state. */
	EXPIRED,

	FAILED,
	UNKNOWN
}
