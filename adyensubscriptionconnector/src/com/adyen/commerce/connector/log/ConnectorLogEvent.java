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
 * Builder for the connector observability lines. Every connector emits {@code key=value} pairs that
 * dashboards and alerts parse, so the line format is a contract rather than prose - and that is the
 * reason this class exists instead of hand-written format strings.
 *
 * <p>Three things go wrong the moment the pairs are concatenated by hand, and all three did:</p>
 * <ul>
 *   <li><b>Unquoted values.</b> A plan code, a vendor error or a webhook field containing a space
 *       splits into two bogus keys and the rest of the line shifts by one. Values are quoted and
 *       escaped here, exactly once, so no call site has to remember.</li>
 *   <li><b>Log forging.</b> Webhook payloads and vendor error bodies are attacker-influenced text.
 *       A newline inside one of them would otherwise start a second, fabricated log line. Control
 *       characters never survive {@link #escape}.</li>
 *   <li><b>Drift.</b> A missing space or a stray comma between two concatenated literals produces a
 *       line that looks right in review and parses wrong in production. Here the separators are not
 *       written by the caller at all.</li>
 * </ul>
 *
 * <p>Values are escaped lazily - the work happens inside SLF4J's own formatting step, so a line that
 * is filtered out by the log level costs nothing beyond the builder itself. {@code null} values drop
 * their key rather than printing a placeholder: an absent field is honest about not being known,
 * where {@code account_id=null} reads like a value.</p>
 *
 * <p>{@code platform}, {@code operation} and {@code correlation_id} are taken from
 * {@link ConnectorLogContext} when a scope is open, which is what lets a transport-level line be
 * joined to the business operation that caused it without the transport having to guess.</p>
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

	/**
	 * A single value never gets to dominate a line. Vendor error bodies and resolved-id collections
	 * are the realistic offenders; both are diagnostics, and the first few hundred characters carry
	 * the diagnosis.
	 */
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

	/**
	 * Starts a line for the named event, pre-filled with whatever {@link ConnectorLogContext} scope is
	 * open on this thread.
	 */
	public static ConnectorLogEvent of(final String event)
	{
		return new ConnectorLogEvent(event);
	}

	/**
	 * Adds a field. The first write of a key wins, so a value inherited from the surrounding scope is
	 * never overwritten by a call site guessing at the same thing. A {@code null} value adds nothing.
	 */
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
		return field("duration_ms", Long.valueOf(elapsedMillis(startedAtNanos)));
	}

	/**
	 * Marks the line as a completed, successful operation: {@code outcome}, {@code duration_ms} and an
	 * explicit {@code error_class=none} so a dashboard can filter on one field across both outcomes.
	 */
	public ConnectorLogEvent success(final long startedAtNanos)
	{
		return outcome(OUTCOME_SUCCESS).durationSince(startedAtNanos).field("error_class", ERROR_CLASS_NONE);
	}

	/**
	 * Marks the line as a failed operation and classifies the exception. Safe to call with {@code null}
	 * - failure logging must never be the thing that throws.
	 */
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

	/**
	 * Logs at WARN or INFO depending on whether the line describes a failure. Saves the two identical
	 * branches at the call sites that log both outcomes of the same call.
	 */
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

	/**
	 * The {@code error_class} label for a connector exception.
	 *
	 * <p>Driven by {@link BillingException#isRetryable()} and the exception type, never by the class
	 * <em>name</em>: name matching silently mislabels every subtype that does not happen to repeat the
	 * word, and {@code SubscriptionProductUndecidableException} - retryable, named nothing like it -
	 * is exactly that case.</p>
	 */
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

	/**
	 * The {@code error_class} label for an HTTP status. Shared by both adapters so the vocabulary
	 * cannot drift apart per platform.
	 */
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

	/**
	 * Renders one logfmt value: bare when it is safe to read unquoted, double-quoted and escaped
	 * otherwise. Anything below a space - CR and LF above all - is replaced rather than escaped,
	 * because a log line must not be able to carry one.
	 */
	static String escape(final String raw)
	{
		if (raw.isEmpty())
		{
			return "\"\"";
		}

		// One character short of the limit when the cut would land between a surrogate pair, so the
		// truncation never leaves a lone surrogate behind.
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

	/**
	 * Anything that a log reader could take for the end of the line. {@link Character#isISOControl}
	 * covers C0, DEL and C1; the two Unicode separators are not control characters but are treated as
	 * line breaks by JSON viewers and editors, which is enough to make them unsafe here.
	 */
	private static boolean breaksTheLine(final char character)
	{
		return Character.isISOControl(character) || character == LINE_SEPARATOR
				|| character == PARAGRAPH_SEPARATOR;
	}

	/**
	 * Defers {@link #escape} until SLF4J actually formats the line, so a suppressed line pays for
	 * nothing.
	 */
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
