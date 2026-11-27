package com.adyen.cockpit.api;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.servlet.http.HttpServletRequest;

/**
 * What a route sees of a request. The body stream can be read only once, but the servlet inspects the body
 * of a dangerous route (reason, confirm) before the handler reads it into its own type, so it is read into
 * bytes the first time and cached.
 */
public class CockpitRequest
{
	/**
	 * The largest body a route accepts. Every cockpit body is a small JSON object (a key, a store uid, a reason),
	 * and the body is held in memory before authorisation of its content, so an unbounded read would let any
	 * signed-in user exhaust the Backoffice heap.
	 */
	public static final int MAX_BODY_BYTES = 64 * 1024;

	private final HttpServletRequest request;
	private final Map<String, String> pathVariables;
	private final String correlationId;
	private byte[] body;
	private JsonNode tree;
	private boolean treeRead;
	private boolean oversized;

	public CockpitRequest(final HttpServletRequest request, final Map<String, String> pathVariables,
			final String correlationId)
	{
		this.request = request;
		this.pathVariables = Map.copyOf(pathVariables);
		this.correlationId = correlationId;
	}

	public String pathVariable(final String name)
	{
		return pathVariables.get(name);
	}

	public String param(final String name)
	{
		return request.getParameter(name);
	}

	public String correlationId()
	{
		return correlationId;
	}

	/**
	 * The body as the given type, or null when there is none or it is not valid JSON for the type.
	 *
	 * @throws CockpitApiException {@code PAYLOAD_TOO_LARGE} when the body exceeds {@link #MAX_BODY_BYTES}
	 */
	public <T> T body(final Class<T> type)
	{
		try
		{
			final byte[] raw = rawBody();
			return raw.length == 0 ? null : CockpitJson.mapper().readValue(raw, type);
		}
		catch (final IOException e)
		{
			return null;
		}
	}

	/** The body as a JSON tree, or null when it is empty or not JSON. Throws like {@link #body}. */
	public synchronized JsonNode json()
	{
		if (!treeRead)
		{
			treeRead = true;
			try
			{
				final byte[] raw = rawBody();
				final JsonNode read = raw.length == 0 ? null : CockpitJson.mapper().readTree(raw);
				tree = read == null || read.isMissingNode() ? null : read;
			}
			catch (final IOException e)
			{
				tree = null;
			}
		}
		return tree;
	}

	private synchronized byte[] rawBody() throws IOException
	{
		if (oversized)
		{
			throw tooLarge();
		}
		if (body == null)
		{
			if (request.getContentLengthLong() > MAX_BODY_BYTES)
			{
				oversized = true;
				throw tooLarge();
			}
			// Bounded read: Content-Length can be absent (chunked) or wrong, so the limit is enforced on the bytes.
			final byte[] read = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
			if (read.length > MAX_BODY_BYTES)
			{
				oversized = true;
				throw tooLarge();
			}
			body = read;
		}
		return body;
	}

	private static CockpitApiException tooLarge()
	{
		return new CockpitApiException(ErrorCode.PAYLOAD_TOO_LARGE, "The request is too large.", "body", null);
	}
}
