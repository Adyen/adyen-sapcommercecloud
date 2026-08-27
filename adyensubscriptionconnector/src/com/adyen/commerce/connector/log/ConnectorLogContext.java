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

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.MDC;

import de.hybris.platform.core.HybrisEnumValue;

/**
 * The connector operation currently running on this thread, published through SLF4J's MDC.
 *
 * <p>It exists to answer a question the lower layers cannot answer for themselves. The HTTP
 * transport sees a method and a URL; it has no idea whether it is carrying a subscription creation
 * or a webhook lookup, and the first attempt to recover that from the URL shape produced labels that
 * were confidently wrong - a GET of a billing info read as {@code import_token}, everything under
 * {@code /subscriptions/} that fell through read as {@code cancel_subscription}. A guess that is
 * usually right is worse than no label, because nothing downstream can tell the two apart.</p>
 *
 * <p>So the adapter states the operation once, at the SPI boundary, and every line logged underneath
 * it - transport, API client, plan resolver - inherits it verbatim through
 * {@link ConnectorLogEvent}.</p>
 *
 * <p>{@link #CORRELATION_ID} is deliberately not set here: it belongs to whatever business action
 * triggered the call (an order code, a webhook delivery), which only the core knows. Anything the
 * core puts under that key travels down into the connector lines automatically, which is how a
 * transport timeout becomes traceable back to an order.</p>
 *
 * <p>Scopes nest and restore: closing puts back exactly what was there before, so an inner scope
 * cannot leak into its caller and a connector call made from inside somebody else's MDC context
 * leaves that context intact.</p>
 */
public final class ConnectorLogContext implements AutoCloseable
{
	public static final String PLATFORM = "platform";
	public static final String OPERATION = "operation";
	public static final String CORRELATION_ID = "correlation_id";

	/** Key to previous value; a {@code null} value means the key was absent before this scope. */
	private final Map<String, String> previous = new LinkedHashMap<>(4);

	private ConnectorLogContext()
	{
		// use the factory methods
	}

	/**
	 * Opens a scope naming the platform and the connector operation. Intended for the SPI entry
	 * points, in a try-with-resources.
	 */
	public static ConnectorLogContext open(final HybrisEnumValue platform, final String operation)
	{
		final ConnectorLogContext context = new ConnectorLogContext();
		context.set(PLATFORM, code(platform));
		context.set(OPERATION, operation);
		return context;
	}

	/**
	 * Opens a scope naming only the business action every line underneath belongs to - an order code,
	 * a webhook delivery id. For the core to use around whatever it drives the connectors with.
	 */
	public static ConnectorLogContext correlate(final String correlationId)
	{
		final ConnectorLogContext context = new ConnectorLogContext();
		context.set(CORRELATION_ID, correlationId);
		return context;
	}

	/**
	 * @return the value currently published under {@code key}, or {@code null} when no scope set it
	 */
	public static String current(final String key)
	{
		return MDC.get(key);
	}

	/**
	 * The stable string for a value used as a log label. Hybris enums answer with their code rather
	 * than a generated {@code toString()} that a future platform version is free to change.
	 */
	public static String code(final Object value)
	{
		if (value == null)
		{
			return null;
		}
		return value instanceof HybrisEnumValue hybrisEnum ? hybrisEnum.getCode() : String.valueOf(value);
	}

	private void set(final String key, final String value)
	{
		if (value == null)
		{
			return;
		}
		previous.put(key, MDC.get(key));
		MDC.put(key, value);
	}

	@Override
	public void close()
	{
		for (final Map.Entry<String, String> entry : previous.entrySet())
		{
			if (entry.getValue() == null)
			{
				MDC.remove(entry.getKey());
			}
			else
			{
				MDC.put(entry.getKey(), entry.getValue());
			}
		}
		previous.clear();
	}
}
