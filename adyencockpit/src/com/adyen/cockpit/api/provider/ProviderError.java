package com.adyen.cockpit.api.provider;

import java.util.LinkedHashMap;
import java.util.Map;

import com.adyen.cockpit.api.CockpitApiException;
import com.adyen.cockpit.api.ErrorCode;

/**
 * A classified provider failure. It carries no provider message and no body: both can echo a key or a
 * submitted value, so only the domain code, the provider, the HTTP status and Retry-After survive.
 *
 * @param status     the provider's HTTP status, null when there was no response
 * @param retryAfter the provider's Retry-After header as sent, null when absent
 */
public record ProviderError(ErrorCode code, Provider provider, Integer status, String retryAfter)
{
	private static final String MESSAGE_PREFIX = "The payment provider call failed: ";

	/** The API exception for this failure; {@code message} is shown to a merchant and must be free of provider text. */
	public CockpitApiException toException(final String message)
	{
		final Map<String, Object> details = new LinkedHashMap<>();
		details.put("provider", provider.wireName());
		if (status != null)
		{
			details.put("status", status);
		}
		if (retryAfter != null)
		{
			details.put("retryAfter", retryAfter);
		}
		return new CockpitApiException(code, message == null ? MESSAGE_PREFIX + code.name() : message, null, details);
	}
}
