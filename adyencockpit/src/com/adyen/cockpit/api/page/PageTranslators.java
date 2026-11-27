package com.adyen.cockpit.api.page;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Turns each provider's paging fields into a {@link Page} (ADR 0002). Provider-neutral: the callers pass the
 * raw fields in, so this depends on no connector extension. Never throws on a missing or odd field.
 */
public class PageTranslators
{
	/** Adyen: {@code _links.next.href} and {@code itemsTotal}; {@code next} is the next page number. */
	public <T> Page<T> fromAdyen(final List<T> items, final PageRequest request, final Map<String, Object> links,
			final Number itemsTotal)
	{
		final String href = nextHref(links);
		final String next = href == null ? null : nextPageNumber(href, request);
		return new Page<>(items, request.pageNumber(), request.pageSize(),
				itemsTotal == null ? null : Long.valueOf(itemsTotal.longValue()), next);
	}

	/** Recurly: {@code has_more} and {@code next}; no total. */
	public <T> Page<T> fromRecurly(final List<T> items, final PageRequest request, final boolean hasMore,
			final String next)
	{
		return new Page<>(items, request.pageNumber(), request.pageSize(), null,
				hasMore && StringUtils.isNotBlank(next) ? next : null);
	}

	/** Chargebee: {@code next_offset}; no total. */
	public <T> Page<T> fromChargebee(final List<T> items, final PageRequest request, final String nextOffset)
	{
		return new Page<>(items, request.pageNumber(), request.pageSize(), null,
				StringUtils.isNotBlank(nextOffset) ? nextOffset : null);
	}

	private static String nextHref(final Map<String, Object> links)
	{
		if (links == null || !(links.get("next") instanceof Map<?, ?> next))
		{
			return null;
		}
		final Object href = next.get("href");
		return href instanceof String text && StringUtils.isNotBlank(text) ? text : null;
	}

	/** The {@code pageNumber} query parameter of the link, else the page after this one. */
	private static String nextPageNumber(final String href, final PageRequest request)
	{
		try
		{
			final String value = UriComponentsBuilder.fromUri(URI.create(href)).build().getQueryParams()
					.getFirst(PageRequest.PAGE_NUMBER);
			// A link that does not move forward would make a client loop: fall back to the next page instead.
			if (value != null && Integer.parseInt(value.trim()) > request.pageNumber())
			{
				return String.valueOf(Integer.parseInt(value.trim()));
			}
		}
		catch (final RuntimeException e)
		{
			// unparsable link: fall back below
		}
		return String.valueOf(request.pageNumber() + 1);
	}
}
