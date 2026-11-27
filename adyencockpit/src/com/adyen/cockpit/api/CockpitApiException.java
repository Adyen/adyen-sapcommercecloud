package com.adyen.cockpit.api;

import java.util.Map;

/**
 * A refusal a route raises on purpose. The servlet turns it into the error envelope with the status of its
 * {@link ErrorCode}. The message is shown to a merchant, so it must not carry an exception text, a submitted
 * value or a provider's error body.
 */
public class CockpitApiException extends RuntimeException
{
	private static final long serialVersionUID = 1L;

	private final ErrorCode code;
	private final String field;
	private final transient Map<String, Object> details;

	public CockpitApiException(final ErrorCode code, final String message)
	{
		this(code, message, null, null);
	}

	public CockpitApiException(final ErrorCode code, final String message, final String field,
			final Map<String, Object> details)
	{
		super(message);
		this.code = java.util.Objects.requireNonNull(code, "code");
		this.field = field;
		this.details = details == null ? Map.of() : Map.copyOf(details);
	}

	public ErrorCode code()
	{
		return code;
	}

	public String field()
	{
		return field;
	}

	public Map<String, Object> details()
	{
		return details;
	}
}
