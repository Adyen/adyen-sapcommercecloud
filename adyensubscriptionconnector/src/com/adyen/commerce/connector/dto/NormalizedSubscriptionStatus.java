package com.adyen.commerce.connector.dto;

/**
 * Subscription lifecycle states understood by the connector core.
 */
public enum NormalizedSubscriptionStatus
{
	PENDING,
	ACTIVE,
	PAST_DUE,
	PAUSED,
	CANCELLED,
	EXPIRED,
	FAILED,
	UNKNOWN
}
