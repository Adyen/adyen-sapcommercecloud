package com.adyen.cockpit.api.page;

import org.apache.commons.lang3.StringUtils;

import com.adyen.cockpit.api.CockpitApiException;
import com.adyen.cockpit.api.CockpitRequest;
import com.adyen.cockpit.api.ErrorCode;

/**
 * The paging parameters of a request. {@code pageNumber} is 1-based (default 1), {@code pageSize} defaults
 * to 20, and {@code next} is the opaque token of the previous page for providers that page by cursor.
 *
 * <p>A {@code pageSize} above {@link #MAX_PAGE_SIZE} is refused with {@code INVALID_REQUEST} rather than
 * clamped: a client that asked for 500 and silently got 100 would believe it had reached the end when the
 * count of items did not match.</p>
 */
public record PageRequest(int pageNumber, int pageSize, String next)
{
	public static final String PAGE_NUMBER = "pageNumber";
	public static final String PAGE_SIZE = "pageSize";
	public static final String NEXT = "next";

	public static final int DEFAULT_PAGE_NUMBER = 1;
	public static final int DEFAULT_PAGE_SIZE = 20;
	public static final int MAX_PAGE_SIZE = 100;

	public static PageRequest from(final CockpitRequest request)
	{
		final int number = positive(request.param(PAGE_NUMBER), PAGE_NUMBER, DEFAULT_PAGE_NUMBER);
		final int size = positive(request.param(PAGE_SIZE), PAGE_SIZE, DEFAULT_PAGE_SIZE);
		if (size > MAX_PAGE_SIZE)
		{
			throw new CockpitApiException(ErrorCode.INVALID_REQUEST, "The page size is above the maximum of "
					+ MAX_PAGE_SIZE + ".", PAGE_SIZE, null);
		}
		return new PageRequest(number, size, StringUtils.trimToNull(request.param(NEXT)));
	}

	private static int positive(final String raw, final String name, final int fallback)
	{
		if (StringUtils.isBlank(raw))
		{
			return fallback;
		}
		try
		{
			final int value = Integer.parseInt(raw.trim());
			if (value >= 1)
			{
				return value;
			}
		}
		catch (final NumberFormatException e)
		{
			// falls through to the refusal below; the submitted value is not echoed
		}
		throw new CockpitApiException(ErrorCode.INVALID_REQUEST, "The parameter must be a whole number of 1 or more.",
				name, null);
	}
}
