package com.adyen.cockpit.api.provider;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URISyntaxException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.adyen.cockpit.api.CockpitJson;
import com.adyen.cockpit.api.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Turns a provider's failed answer, or a failure to get one, into a {@link ProviderError} (ADR 0002). It is
 * provider-neutral and depends on no connector extension: the three provider body shapes are read here
 * from the raw JSON.
 *
 * <p>The status decides, except where a provider reports a parameter error with a status that says
 * nothing: Chargebee answers those with 400 and an {@code api_error_code} such as {@code invalid_request}
 * or {@code param_wrong_value}, Recurly with the {@code validation} error type ({@code invalid_api_version} and {@code invalid_content_type} are
 * the caller's own mistakes, not content the provider refused). Parsing
 * never throws, because it runs while a failure is already being handled; a body that is empty or not
 * JSON is classified by status alone. Nothing from the body is kept except those codes, and they are never
 * returned or logged.</p>
 */
public class ProviderErrorClassifier
{
	private static final int MAX_CAUSE_DEPTH = 16;
	private static final Set<String> CHARGEBEE_VALIDATION_CODES = Set.of("invalid_request", "param_wrong_value",
			"param_not_present", "param_invalid");

	public ProviderError classify(final Provider provider, final int status, final Map<String, List<String>> headers,
			final String body)
	{
		return switch (status)
		{
			case 401 -> new ProviderError(ErrorCode.PROVIDER_UNAUTHORISED, provider, status, null);
			case 403 -> new ProviderError(ErrorCode.PROVIDER_FORBIDDEN, provider, status, null);
			case 404 -> new ProviderError(ErrorCode.PROVIDER_NOT_FOUND, provider, status, null);
			case 422 -> new ProviderError(ErrorCode.PROVIDER_VALIDATION, provider, status, null);
			case 429 -> new ProviderError(ErrorCode.PROVIDER_RATE_LIMITED, provider, status, retryAfter(headers));
			default -> new ProviderError(status == 400 && isValidationBody(provider, body)
					? ErrorCode.PROVIDER_VALIDATION : ErrorCode.PROVIDER_REJECTED, provider, status, null);
		};
	}

	public ProviderError classifyTransport(final Provider provider, final Throwable failure)
	{
		ErrorCode code = ErrorCode.PROVIDER_UNREACHABLE;
		boolean misconfigured = false;
		// A timeout anywhere in the chain wins: Spring wraps it in a ResourceAccessException.
		int depth = 0;
		for (Throwable t = failure; t != null && depth < MAX_CAUSE_DEPTH; t = t.getCause() == t ? null : t.getCause(), depth++)
		{
			if (t instanceof SocketTimeoutException || t instanceof HttpTimeoutException)
			{
				return new ProviderError(ErrorCode.PROVIDER_TIMEOUT, provider, null, null);
			}
			if (t instanceof IllegalArgumentException || t instanceof URISyntaxException)
			{
				misconfigured = true;
			}
		}
		if (misconfigured)
		{
			code = ErrorCode.PROVIDER_MISCONFIGURED;
		}
		// Everything else - UnknownHost, ConnectException, SSL, any other IOException - is "no connection".
		return new ProviderError(code, provider, null, null);
	}

	protected String retryAfter(final Map<String, List<String>> headers)
	{
		if (headers == null)
		{
			return null;
		}
		for (final Map.Entry<String, List<String>> header : headers.entrySet())
		{
			if (header.getKey() != null && "retry-after".equals(header.getKey().toLowerCase(Locale.ROOT))
					&& header.getValue() != null)
			{
				return header.getValue().stream().filter(StringUtils::isNotBlank).map(String::trim).findFirst()
						.orElse(null);
			}
		}
		return null;
	}

	protected boolean isValidationBody(final Provider provider, final String body)
	{
		final JsonNode node = readTree(body);
		if (node == null)
		{
			return false;
		}
		return switch (provider)
		{
			case CHARGEBEE -> isChargebeeValidationCode(text(node.path("api_error_code")))
					|| "invalid_request".equals(text(node.path("type")));
			case RECURLY ->
			{
				final String type = StringUtils.defaultString(
						StringUtils.defaultString(text(node.path("type")), text(node.path("error").path("type"))));
				yield "validation".equals(type);
			}
			case ADYEN -> false;
		};
	}

	private static boolean isChargebeeValidationCode(final String code)
	{
		return code != null && CHARGEBEE_VALIDATION_CODES.contains(code);
	}

	private static String text(final JsonNode node)
	{
		return node.isTextual() ? node.asText() : null;
	}

	private static JsonNode readTree(final String body)
	{
		if (StringUtils.isBlank(body))
		{
			return null;
		}
		try
		{
			final JsonNode node = CockpitJson.mapper().readTree(body);
			return node != null && node.isObject() ? node : null;
		}
		catch (final IOException | RuntimeException e)
		{
			return null;
		}
	}
}
