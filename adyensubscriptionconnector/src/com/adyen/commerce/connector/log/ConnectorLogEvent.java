/*
 *                        ######
 *                        ######
 *  ############    ####( ######  #####. ######  ############   ############
 *  #############  #####( ######  #####. ######  #############  #############
 *         ######  #####( ######  #####. ######  #####  ######  #####  ######
 *  ###### ######  #####( ######  #####. ######  #####  #####   #####  ######
 *  ###### ######  #####( ######  #####. ######  #####          #####  ######
 *  #############  #############  #############  #############  #####  ######
 *   ############   ############  #############   ############  #####  ######
 *                                       ######
 *                                #############
 *                                ############
 *
 *  Adyen Hybris Extension
 *
 *  Copyright (c) 2026 Adyen B.V.
 *  This file is open source and available under the MIT license.
 *  See the LICENSE file for more info.
 */
package com.adyen.commerce.connector.log;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;

import com.adyen.commerce.connector.exception.BillingException;
import com.adyen.commerce.connector.exception.ConnectorNotConfiguredException;
import com.adyen.commerce.connector.exception.PlanNotMappedException;
import com.adyen.commerce.connector.exception.PreconditionFailedException;

/**
 * Builder for connector log lines in logfmt ({@code key=value}). Values are escaped here, so untrusted text
 * cannot forge a line; a {@code null} value drops its key; escaping runs only when the line is logged.
 * {@code platform}, {@code operation} and {@code correlation_id} come from an open {@link ConnectorLogContext}.
 */
public final class ConnectorLogEvent
{
	public static final String OUTCOME_SUCCESS = "success";
	public static final String OUTCOME_FAILURE = "failure";
	public static final String OUTCOME_IGNORED = "ignored";
	public static final String OUTCOME_UNRESOLVED = "unresolved";

	public static final String ERROR_CLASS_NONE = "none";
	public static final String ERROR_CLASS_VALIDATION = "validation";
	public static final String ERROR_CLASS_CONFIGURATION = "configuration";
	public static final String ERROR_CLASS_RETRYABLE = "remote_retryable";
	public static final String ERROR_CLASS_TERMINAL = "remote_terminal";
	public static final String ERROR_CLASS_RATE_LIMIT = "rate_limit";
	public static final String ERROR_CLASS_REMOTE_5XX = "remote_5xx";
	public static final String ERROR_CLASS_REMOTE_4XX = "remote_4xx";
	public static final String ERROR_CLASS_UNEXPECTED_STATUS = "unexpected_status";

	/** Caps one value so a vendor error body or an id collection cannot dominate the line. */
	static final int MAX_VALUE_LENGTH = 512;

	private static final String TRUNCATION_MARKER = "...";

	/** Not ISO controls, but a line break to a JSON viewer or an editor all the same. */
	private static final char LINE_SEPARATOR = '\u2028';
	private static final char PARAGRAPH_SEPARATOR = '\u2029';

	private final Map<String, Object> fields = new LinkedHashMap<>();

	private ConnectorLogEvent(final String event)
	{
		field("event", event);
		field(ConnectorLogContext.PLATFORM, ConnectorLogContext.current(ConnectorLogContext.PLATFORM));
		field(ConnectorLogContext.OPERATION, ConnectorLogContext.current(ConnectorLogContext.OPERATION));
		field(ConnectorLogContext.CORRELATION_ID, ConnectorLogContext.current(ConnectorLogContext.CORRELATION_ID));
	}

	public static ConnectorLogEvent of(final String event)
	{
		return new ConnectorLogEvent(event);
	}

	/** Adds a field; the first write of a key wins, so the surrounding scope's values are kept. */
	public ConnectorLogEvent field(final String key, final Object value)
	{
		if (key != null && value != null)
		{
			fields.putIfAbsent(key, value);
		}
		return this;
	}

	public ConnectorLogEvent platform(final Object platform)
	{
		return field(ConnectorLogContext.PLATFORM, ConnectorLogContext.code(platform));
	}

	public ConnectorLogEvent operation(final String operation)
	{
		return field(ConnectorLogContext.OPERATION, operation);
	}

	public ConnectorLogEvent outcome(final String outcome)
	{
		return field("outcome", outcome);
	}

	public ConnectorLogEvent reason(final String reason)
	{
		return field("reason", reason);
	}

	/**
	 * @param startedAtNanos a {@link System#nanoTime()} reading taken when the operation began
	 */
	public ConnectorLogEvent durationSince(final long startedAtNanos)
	{
		return field("duration_ms", elapsedMillis(startedAtNanos));
	}

	/** Success, with an explicit {@code error_class=none} so both outcomes filter on one field. */
	public ConnectorLogEvent success(final long startedAtNanos)
	{
		return outcome(OUTCOME_SUCCESS).durationSince(startedAtNanos).field("error_class", ERROR_CLASS_NONE);
	}

	/** Failure, classified by the exception; accepts {@code null}. */
	public ConnectorLogEvent failure(final long startedAtNanos, final BillingException error)
	{
		return outcome(OUTCOME_FAILURE)
				.durationSince(startedAtNanos)
				.field("error_class", errorClass(error))
				.field("exception_class", error == null ? null : error.getClass().getName());
	}

	public void info(final Logger log)
	{
		if (log.isInfoEnabled())
		{
			log.info(pattern(), values());
		}
	}

	public void debug(final Logger log)
	{
		if (log.isDebugEnabled())
		{
			log.debug(pattern(), values());
		}
	}

	public void warn(final Logger log)
	{
		if (log.isWarnEnabled())
		{
			log.warn(pattern(), values());
		}
	}

	public void error(final Logger log)
	{
		if (log.isErrorEnabled())
		{
			log.error(pattern(), values());
		}
	}

	/** Logs at WARN or INFO depending on whether the line describes a failure. */
	public void log(final Logger log, final boolean failed)
	{
		if (failed)
		{
			warn(log);
		}
		else
		{
			info(log);
		}
	}

	/** The {@code error_class} label for a connector exception, by retryability and type. */
	public static String errorClass(final BillingException error)
	{
		if (error == null)
		{
			return null;
		}
		if (error.isRetryable())
		{
			return ERROR_CLASS_RETRYABLE;
		}
		if (error instanceof ConnectorNotConfiguredException || error instanceof PlanNotMappedException)
		{
			return ERROR_CLASS_CONFIGURATION;
		}
		if (error instanceof PreconditionFailedException)
		{
			return ERROR_CLASS_VALIDATION;
		}
		return ERROR_CLASS_TERMINAL;
	}

	/** The {@code error_class} label for an HTTP status, shared by all adapters. */
	public static String httpErrorClass(final int status)
	{
		if (status >= 200 && status < 300)
		{
			return ERROR_CLASS_NONE;
		}
		if (status == 429)
		{
			return ERROR_CLASS_RATE_LIMIT;
		}
		if (status >= 500)
		{
			return ERROR_CLASS_REMOTE_5XX;
		}
		if (status >= 400)
		{
			return ERROR_CLASS_REMOTE_4XX;
		}
		return ERROR_CLASS_UNEXPECTED_STATUS;
	}

	public static long elapsedMillis(final long startedAtNanos)
	{
		return (System.nanoTime() - startedAtNanos) / 1_000_000L;
	}

	String pattern()
	{
		final StringBuilder pattern = new StringBuilder(32 * fields.size());
		for (final String key : fields.keySet())
		{
			if (pattern.length() > 0)
			{
				pattern.append(' ');
			}
			pattern.append(key).append("={}");
		}
		return pattern.toString();
	}

	Object[] values()
	{
		final List<Object> values = new ArrayList<>(fields.size());
		for (final Object value : fields.values())
		{
			values.add(new LazyValue(value));
		}
		return values.toArray();
	}

	/** One logfmt value: bare when safe, else quoted and escaped; line breaks become spaces. */
	static String escape(final String raw)
	{
		if (raw.isEmpty())
		{
			return "\"\"";
		}

		// Never cut between a surrogate pair.
		final String capped;
		if (raw.length() > MAX_VALUE_LENGTH)
		{
			final int end = Character.isHighSurrogate(raw.charAt(MAX_VALUE_LENGTH - 1))
					? MAX_VALUE_LENGTH - 1
					: MAX_VALUE_LENGTH;
			capped = raw.substring(0, end) + TRUNCATION_MARKER;
		}
		else
		{
			capped = raw;
		}

		boolean quote = false;
		for (int index = 0; index < capped.length(); index++)
		{
			if (needsQuoting(capped.charAt(index)))
			{
				quote = true;
				break;
			}
		}
		if (!quote)
		{
			return capped;
		}

		final StringBuilder escaped = new StringBuilder(capped.length() + 8).append('"');
		for (int index = 0; index < capped.length(); index++)
		{
			final char character = capped.charAt(index);
			if (character == '"' || character == '\\')
			{
				escaped.append('\\').append(character);
			}
			else if (breaksTheLine(character))
			{
				escaped.append(' ');
			}
			else
			{
				escaped.append(character);
			}
		}
		return escaped.append('"').toString();
	}

	private static boolean needsQuoting(final char character)
	{
		return character <= ' ' || character == '"' || character == '=' || character == '\\'
				|| breaksTheLine(character);
	}

	/** ISO controls plus the Unicode line and paragraph separators, which viewers also treat as breaks. */
	private static boolean breaksTheLine(final char character)
	{
		return Character.isISOControl(character) || character == LINE_SEPARATOR
				|| character == PARAGRAPH_SEPARATOR;
	}

	/** Defers {@link #escape} until SLF4J formats the line. */
	private static final class LazyValue
	{
		private final Object value;

		private LazyValue(final Object value)
		{
			this.value = value;
		}

		@Override
		public String toString()
		{
			return escape(String.valueOf(value));
		}
	}
}
