package com.adyen.commerce.connector.dto;

import java.time.Instant;

/**
 * Authoritative, vendor-neutral snapshot returned by a billing platform.
 */
public record NormalizedSubscription(
		BillingSubscriptionRef subscription,
		NormalizedSubscriptionStatus status,
		String planId,
		Integer quantity,
		Instant currentPeriodStart,
		Instant currentPeriodEnd,
		boolean cancelAtPeriodEnd,
		Instant platformUpdatedAt)
{
	public NormalizedSubscription
	{
		Dtos.requireValue(subscription, "subscription");
		Dtos.requireValue(status, "status");
	}
}
